#!/bin/bash

upsert_connector() {
  CONNECTOR_NAME="$1"
  CONF_DATA="$2"

  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://connect:8086/connectors/$CONNECTOR_NAME")

  if [ "$STATUS" -eq 200 ]; then
    echo "✅ 이미 '$CONNECTOR_NAME'이(가) 등록되어 있습니다. 재등록을 건너뜁니다."
  else
    echo "🚀 '$CONNECTOR_NAME' 커넥터를 새로 등록합니다."
    curl -i -X POST -H "Content-Type:application/json" \
      http://connect:8086/connectors/ -d "$CONF_DATA"
  fi
}

ASGARD_CONNECTOR_NAME="dseum-asgard-connector"
ASGARD_CONF='{
  "name": "'$ASGARD_CONNECTOR_NAME'",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "product-database",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "1234",
    "database.dbname": "dseum_product",
    "plugin.name": "pgoutput",
    "topic.prefix": "asgard",
    "table.include.list": "public.products",
    "slot.name": "asgard_slot",
    "publication.name": "asgard_publication",
    "publication.autocreate.mode": "all_tables",
    "snapshot.mode": "always"
  }
}'

BOARD_NOTICE_CONNECTOR_NAME="dseum-board-notice-connector"
BOARD_NOTICE_CONF='{
  "name": "'$BOARD_NOTICE_CONNECTOR_NAME'",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "board-database",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "1234",
    "database.dbname": "dseum_board",
    "plugin.name": "pgoutput",
    "topic.prefix": "asgard",
    "table.include.list": "public.notices",
    "slot.name": "board_notice_slot",
    "publication.name": "board_notice_publication",
    "publication.autocreate.mode": "all_tables",
    "snapshot.mode": "always"
  }
}'

BOARD_FAQ_CONNECTOR_NAME="dseum-board-faq-connect"
BOARD_FAQ_CONF='{
  "name": "'$BOARD_FAQ_CONNECTOR_NAME'",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "board-database",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "1234",
    "database.dbname": "dseum_board",
    "plugin.name": "pgoutput",
    "topic.prefix": "asgard",
    "table.include.list": "public.faq",
    "slot.name": "board_faq_slot",
    "publication.name": "board_faq_publication",
    "publication.autocreate.mode": "all_tables",
    "snapshot.mode": "always"
  }
}'

REVIEW_CONNECTOR_NAME="dseum-review-connector"
REVIEW_CONF='{
  "name": "'$REVIEW_CONNECTOR_NAME'",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "review-database",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "1234",
    "database.dbname": "dseum_review",
    "plugin.name": "pgoutput",
    "topic.prefix": "reviewdb",
    "table.include.list": "public.reviews",
    "slot.name": "review_slot",
    "publication.name": "review_publication",
    "publication.autocreate.mode": "all_tables"
  }
}'

ORDER_OUTBOX_CONNECTOR_NAME="dseum-order-outbox-connector"
ORDER_OUTBOX_CONF='{
  "name": "'$ORDER_OUTBOX_CONNECTOR_NAME'",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "order-database",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "1234",
    "database.dbname": "dseum_order",
    "plugin.name": "pgoutput",
    "topic.prefix": "orderdb",
    "table.include.list": "public.order_outbox",
    "slot.name": "order_outbox_slot",
    "publication.name": "order_outbox_publication",
    "publication.autocreate.mode": "filtered",
    "tombstones.on.delete": "false",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "event_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "transforms.outbox.table.expand.json.payload": "true"
  }
}'

INVENTORY_OUTBOX_CONNECTOR_NAME="dseum-inventory-outbox-connector"
INVENTORY_OUTBOX_CONF='{
  "name": "'$INVENTORY_OUTBOX_CONNECTOR_NAME'",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "inventory-database",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "1234",
    "database.dbname": "dseum_inventory",
    "plugin.name": "pgoutput",
    "topic.prefix": "inventory_outbox",
    "table.include.list": "public.inventory_outbox",
    "slot.name": "inventory_outbox_slot",
    "publication.name": "inventory_outbox_publication",
    "publication.autocreate.mode": "filtered",
    "snapshot.mode": "initial",
    "tombstones.on.delete": "false",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.route.by.field": "topic",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "transforms.outbox.table.field.event.id": "event_id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.table.expand.json.payload": "true",
    "transforms.outbox.table.fields.additional.placement": "event_id:header:eventId,event_type:header:eventType,aggregate_type:header:aggregateType"
  }
}'

PAYMENT_OUTBOX_CONNECTOR_NAME="dseum-payment-outbox-connector"
PAYMENT_OUTBOX_CONF='{
  "name": "'$PAYMENT_OUTBOX_CONNECTOR_NAME'",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "payment-database",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "1234",
    "database.dbname": "dseum_payment",
    "plugin.name": "pgoutput",
    "topic.prefix": "paymentdb",
    "table.include.list": "public.outbox_events",
    "slot.name": "payment_outbox_slot",
    "publication.name": "payment_outbox_publication",
    "publication.autocreate.mode": "filtered",
    "snapshot.mode": "never",
    "tombstones.on.delete": "false",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.route.by.field": "event_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.table.expand.json.payload": "true",
    "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType,aggregate_type:header:aggregateType"
  }
}'

upsert_connector "$ASGARD_CONNECTOR_NAME" "$ASGARD_CONF"
upsert_connector "$BOARD_NOTICE_CONNECTOR_NAME" "$BOARD_NOTICE_CONF"
upsert_connector "$BOARD_FAQ_CONNECTOR_NAME" "$BOARD_FAQ_CONF"
upsert_connector "$REVIEW_CONNECTOR_NAME" "$REVIEW_CONF"
upsert_connector "$ORDER_OUTBOX_CONNECTOR_NAME" "$ORDER_OUTBOX_CONF"
upsert_connector "$INVENTORY_OUTBOX_CONNECTOR_NAME" "$INVENTORY_OUTBOX_CONF"
upsert_connector "$PAYMENT_OUTBOX_CONNECTOR_NAME" "$PAYMENT_OUTBOX_CONF"

echo -e "\n\n🔍 현재 등록된 커넥터 목록:"
curl -s http://connect:8086/connectors
