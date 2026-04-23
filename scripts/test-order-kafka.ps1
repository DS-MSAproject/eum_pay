param(
    [ValidateSet("normal", "inventoryReservationFailed", "paymentFailed", "inventoryDeductionFailed", "inventoryReleaseFailed")]
    [string]$Scenario = "normal",

    [switch]$SkipStartup,

    [switch]$CheckOrderCheckedOutTopic
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $RepoRoot "docker-compose\docker-compose.yml"

if (-not (Test-Path $ComposeFile)) {
    throw "docker-compose file not found: $ComposeFile"
}

$BootstrapServers = "kafka:9092,kafka2:9092,kafka3:9092"
$OrderBaseUrl = "http://localhost:8082"
$ProductBaseUrl = "http://localhost:8081"

function Invoke-Compose {
    param([string[]]$ComposeArgs)

    & docker compose -f $ComposeFile @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: docker compose -f $ComposeFile $($ComposeArgs -join ' ')"
    }
}

function Wait-HttpUp {
    param(
        [string]$Url,
        [int]$TimeoutSec = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 5
            if ($response.status -eq "UP") {
                Write-Host "[OK] $Url"
                return
            }
        } catch {
        }

        Start-Sleep -Seconds 3
    }

    throw "Timed out waiting for health endpoint: $Url"
}

function Get-TestProductOption {
    $snapshotUrl = "$ProductBaseUrl/internal/products/snapshots?size=100"
    $snapshot = Invoke-RestMethod -Uri $snapshotUrl -Method Get -TimeoutSec 15

    $candidate = $snapshot.items |
        Where-Object { $_.active -eq $true -and $_.options -and $_.options.Count -gt 0 } |
        Select-Object -First 1

    if (-not $candidate) {
        throw "No active product with options was found from $snapshotUrl"
    }

    $option = $candidate.options | Select-Object -First 1

    return [pscustomobject]@{
        productId   = [long]$candidate.productId
        optionId    = [long]$option.optionId
        productName = [string]$candidate.productName
        optionName  = [string]$option.optionName
    }
}

function Submit-Order {
    param(
        [long]$ProductId,
        [long]$OptionId
    )

    $body = @{
        user_id        = 1
        receiver_name  = "codex-tester"
        receiver_phone = "010-1234-5678"
        receiver_addr  = "Seoul"
        items          = @(
            @{
                productId = $ProductId
                optionId  = $OptionId
                quantity  = 1
            }
        )
    } | ConvertTo-Json -Depth 10

    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "$OrderBaseUrl/orders" `
        -ContentType "application/json" `
        -Body $body `
        -TimeoutSec 30

    $responseText = [string]$response

    if ($responseText -match "(\d+)") {
        return [long]$Matches[1]
    }

    throw "Failed to parse order id from response: $responseText"
}

function Publish-KafkaJson {
    param(
        [string]$Topic,
        [hashtable]$Payload
    )

    $json = $Payload | ConvertTo-Json -Compress -Depth 10
    $command = "kafka-console-producer --bootstrap-server $BootstrapServers --topic $Topic"

    $json | & docker exec -i kafka bash -lc $command | Out-Null

    if ($LASTEXITCODE -ne 0) {
        throw "Kafka publish failed for topic=$Topic"
    }

    Write-Host "[PUBLISH] $Topic -> $json"
}

function Get-OrderDetail {
    param([long]$OrderId)

    return Invoke-RestMethod -Uri "$OrderBaseUrl/orders/$OrderId" -Method Get -TimeoutSec 15
}

function Wait-OrderState {
    param(
        [long]$OrderId,
        [string]$ExpectedState,
        [int]$TimeoutSec = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)

    while ((Get-Date) -lt $deadline) {
        try {
            $detail = Get-OrderDetail -OrderId $OrderId
            $currentState = [string]$detail.orderState

            Write-Host "[STATE] orderId=$OrderId current=$currentState expected=$ExpectedState"

            if ($currentState -eq $ExpectedState) {
                return $detail
            }
        } catch {
        }

        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for order state. orderId=$OrderId expected=$ExpectedState"
}

function Check-OrderCheckedOutTopicMessage {
    param([long]$OrderId)

    Write-Host "[INFO] Starting services required for outbox connector"
    Invoke-Compose -ComposeArgs @("up", "-d", "elasticsearch", "connect", "connector-init")

    Start-Sleep -Seconds 20

    $command = "kafka-console-consumer --bootstrap-server $BootstrapServers --topic OrderCheckedOut --from-beginning --timeout-ms 15000"
    $messages = & docker exec kafka bash -lc $command

    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Failed while consuming OrderCheckedOut topic."
        return
    }

    $matched = $messages | Where-Object {
        $_ -match """orderId"":\s*$OrderId" -or $_ -match """order_id"":\s*$OrderId"
    }

    if ($matched) {
        Write-Host "[OK] Found OrderCheckedOut event for orderId=$OrderId"
        $matched | Select-Object -First 1 | ForEach-Object { Write-Host $_ }
    } else {
        Write-Warning "OrderCheckedOut event for orderId=$OrderId was not found."
    }
}

if (-not $SkipStartup) {
    $baseServices = @(
        "kafka", "kafka2", "kafka3",
        "configserver", "eurekaserver", "vault",
        "order-database", "product-database",
        "dseumproducts", "dseumorders"
    )

    Write-Host "[INFO] Starting base services"
    Invoke-Compose -ComposeArgs ((@("up", "-d")) + $baseServices)
}

Wait-HttpUp -Url "$ProductBaseUrl/actuator/health" -TimeoutSec 180
Wait-HttpUp -Url "$OrderBaseUrl/actuator/health" -TimeoutSec 180

$product = Get-TestProductOption
Write-Host "[INFO] Using productId=$($product.productId), optionId=$($product.optionId), productName=$($product.productName), optionName=$($product.optionName)"

$orderId = Submit-Order -ProductId $product.productId -OptionId $product.optionId
Write-Host "[INFO] Created orderId=$orderId"

Wait-OrderState -OrderId $orderId -ExpectedState "ORDER_CHECKED_OUT" -TimeoutSec 30 | Out-Null

if ($CheckOrderCheckedOutTopic) {
    Check-OrderCheckedOutTopicMessage -OrderId $orderId
}

switch ($Scenario) {
    "normal" {
        Publish-KafkaJson -Topic "InventoryReserved" -Payload @{
            eventId   = "test-inventory-reserved-$orderId"
            eventType = "InventoryReserved"
            order_id  = $orderId
            success   = $true
            items     = @(
                @{
                    productId = $product.productId
                    optionId  = $product.optionId
                    quantity  = 1
                }
            )
        }
        Wait-OrderState -OrderId $orderId -ExpectedState "INVENTORY_RESERVED" | Out-Null

        Publish-KafkaJson -Topic "PaymentCompleted" -Payload @{
            eventId       = "test-payment-completed-$orderId"
            eventType     = "PaymentCompleted"
            order_id      = $orderId
            user_id       = 1
            amount        = 10000
            paymentMethod = "CARD"
            paidAmount    = 10000
        }
        Wait-OrderState -OrderId $orderId -ExpectedState "PAYMENT_COMPLETED" | Out-Null

        Publish-KafkaJson -Topic "InventoryDeducted" -Payload @{
            eventId   = "test-inventory-deducted-$orderId"
            eventType = "InventoryDeducted"
            order_id  = $orderId
            success   = $true
        }
        $final = Wait-OrderState -OrderId $orderId -ExpectedState "ORDER_COMPLETED"
    }

    "inventoryReservationFailed" {
        Publish-KafkaJson -Topic "InventoryReservationFailed" -Payload @{
            eventId   = "test-inventory-reservation-failed-$orderId"
            eventType = "InventoryReservationFailed"
            order_id  = $orderId
            success   = $false
            reason    = "mock inventory reservation failed"
            items     = @(
                @{
                    productId = $product.productId
                    optionId  = $product.optionId
                    quantity  = 1
                }
            )
        }
        $final = Wait-OrderState -OrderId $orderId -ExpectedState "INVENTORY_RESERVATION_FAILED"
    }

    "paymentFailed" {
        Publish-KafkaJson -Topic "InventoryReserved" -Payload @{
            eventId   = "test-inventory-reserved-$orderId"
            eventType = "InventoryReserved"
            order_id  = $orderId
            success   = $true
            items     = @(
                @{
                    productId = $product.productId
                    optionId  = $product.optionId
                    quantity  = 1
                }
            )
        }
        Wait-OrderState -OrderId $orderId -ExpectedState "INVENTORY_RESERVED" | Out-Null

        Publish-KafkaJson -Topic "PaymentFailed" -Payload @{
            eventId        = "test-payment-failed-$orderId"
            eventType      = "PaymentFailed"
            order_id       = $orderId
            user_id        = 1
            amount         = 10000
            failure_code   = "MOCK_FAIL"
            failure_reason = "mock payment failed"
            reason         = "mock payment failed"
        }
        Wait-OrderState -OrderId $orderId -ExpectedState "PAYMENT_FAILED" | Out-Null

        Publish-KafkaJson -Topic "InventoryReleased" -Payload @{
            eventId   = "test-inventory-released-$orderId"
            eventType = "InventoryReleased"
            order_id  = $orderId
            success   = $true
        }
        $final = Wait-OrderState -OrderId $orderId -ExpectedState "INVENTORY_RELEASED"
    }

    "inventoryDeductionFailed" {
        Publish-KafkaJson -Topic "InventoryReserved" -Payload @{
            eventId   = "test-inventory-reserved-$orderId"
            eventType = "InventoryReserved"
            order_id  = $orderId
            success   = $true
            items     = @(
                @{
                    productId = $product.productId
                    optionId  = $product.optionId
                    quantity  = 1
                }
            )
        }
        Wait-OrderState -OrderId $orderId -ExpectedState "INVENTORY_RESERVED" | Out-Null

        Publish-KafkaJson -Topic "PaymentCompleted" -Payload @{
            eventId       = "test-payment-completed-$orderId"
            eventType     = "PaymentCompleted"
            order_id      = $orderId
            user_id       = 1
            amount        = 10000
            paymentMethod = "CARD"
            paidAmount    = 10000
        }
        Wait-OrderState -OrderId $orderId -ExpectedState "PAYMENT_COMPLETED" | Out-Null

        Publish-KafkaJson -Topic "InventoryDeductionFailed" -Payload @{
            eventId   = "test-inventory-deduction-failed-$orderId"
            eventType = "InventoryDeductionFailed"
            order_id  = $orderId
            success   = $false
            reason    = "mock inventory deduction failed"
        }
        $final = Wait-OrderState -OrderId $orderId -ExpectedState "INVENTORY_DEDUCTION_FAILED"
    }

    "inventoryReleaseFailed" {
        Publish-KafkaJson -Topic "InventoryReserved" -Payload @{
            eventId   = "test-inventory-reserved-$orderId"
            eventType = "InventoryReserved"
            order_id  = $orderId
            success   = $true
            items     = @(
                @{
                    productId = $product.productId
                    optionId  = $product.optionId
                    quantity  = 1
                }
            )
        }
        Wait-OrderState -OrderId $orderId -ExpectedState "INVENTORY_RESERVED" | Out-Null

        Publish-KafkaJson -Topic "PaymentFailed" -Payload @{
            eventId        = "test-payment-failed-$orderId"
            eventType      = "PaymentFailed"
            order_id       = $orderId
            user_id        = 1
            amount         = 10000
            failure_code   = "MOCK_FAIL"
            failure_reason = "mock payment failed"
            reason         = "mock payment failed"
        }
        Wait-OrderState -OrderId $orderId -ExpectedState "PAYMENT_FAILED" | Out-Null

        Publish-KafkaJson -Topic "InventoryReleaseFailed" -Payload @{
            eventId   = "test-inventory-release-failed-$orderId"
            eventType = "InventoryReleaseFailed"
            order_id  = $orderId
            success   = $false
            reason    = "mock inventory release failed"
        }
        $final = Wait-OrderState -OrderId $orderId -ExpectedState "INVENTORY_RELEASE_FAILED"
    }
}

Write-Host ""
Write-Host "===== FINAL ORDER DETAIL ====="
$final | ConvertTo-Json -Depth 10
