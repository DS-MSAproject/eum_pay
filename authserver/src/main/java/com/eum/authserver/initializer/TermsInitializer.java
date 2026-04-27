package com.eum.authserver.initializer;

import com.eum.authserver.entity.Terms;
import com.eum.authserver.repository.TermsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 약관 데이터 자동 초기화
 *
 * docker-compose down -v로 DB 초기화해도
 * 서버 재시작 시 자동으로 약관 데이터가 주입됨
 *
 * 실행 순서: @SpringBootApplication → Bean 생성 → ApplicationRunner 실행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TermsInitializer implements ApplicationRunner {

    private final TermsRepository termsRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("========== 약관 데이터 초기화 시작 ==========");

        // 1. 기존 약관이 있는지 확인 (중복 초기화 방지)
        if (termsRepository.count() > 0) {
            log.info("약관 데이터가 이미 존재합니다. 초기화 스킵.");
            return;
        }

        // 2. 서비스 이용약관
        Terms serviceTerms = Terms.builder()
                .termId("service_terms")
                .title("서비스 이용약관")
                .content(getServiceTermsContent())
                .isRequired(true)
                .version("1.0")
                .active(true)
                .build();
        termsRepository.save(serviceTerms);
        log.info("✓ 서비스 이용약관 저장 완료");

        // 3. 개인정보보호정책
        Terms privacyPolicy = Terms.builder()
                .termId("privacy_policy")
                .title("개인정보보호정책")
                .content(getPrivacyPolicyContent())
                .isRequired(true)
                .version("1.0")
                .active(true)
                .build();
        termsRepository.save(privacyPolicy);
        log.info("✓ 개인정보보호정책 저장 완료");

        // 4. SMS 마케팅 동의
        Terms marketingSms = Terms.builder()
                .termId("marketing_sms")
                .title("SMS 마케팅 정보 수신 동의")
                .content(getMarketingSmsContent())
                .isRequired(false)
                .version("1.0")
                .active(true)
                .build();
        termsRepository.save(marketingSms);
        log.info("✓ SMS 마케팅 동의 저장 완료");

        // 5. 이메일 마케팅 동의
        Terms marketingEmail = Terms.builder()
                .termId("marketing_email")
                .title("이메일 마케팅 정보 수신 동의")
                .content(getMarketingEmailContent())
                .isRequired(false)
                .version("1.0")
                .active(true)
                .build();
        termsRepository.save(marketingEmail);
        log.info("✓ 이메일 마케팅 동의 저장 완료");

        log.info("========== 약관 데이터 초기화 완료 ==========");
    }

    /**
     * 서비스 이용약관 내용
     * 실제 운영 환경에서는 이 내용을 파일로 분리하거나
     * 별도 설정 서버(Spring Cloud Config)에서 관리 가능
     */
    private String getServiceTermsContent() {
        return """
                ■ 이음펫푸드 서비스 이용약관

                제1조 (목적)
                이 약관은 이음펫푸드(이하 "회사")가 제공하는 서비스(이하 "서비스")를 이용하기 위한
                회사와 이용자 간의 권리, 의무 및 책임사항, 기타 필요한 사항을 규정함을 목적으로 합니다.

                제2조 (정의)
                1. "서비스"란 회사가 제공하는 모든 온라인/오프라인 서비스를 의미합니다.
                2. "이용자"란 이 약관에 동의하고 서비스를 이용하는 자를 의미합니다.
                3. "계정"이란 이용자의 식별과 서비스 이용을 위하여 설정된 이용자의 고유 ID입니다.

                제3조 (약관의 효력 및 변경)
                1. 이 약관은 이용자가 동의함으로써 효력이 발생합니다.
                2. 회사는 필요하다고 인정할 경우 이 약관을 변경할 수 있습니다.
                3. 약관이 변경될 경우 회사는 최소 30일 전 공지하고, 이용자는 변경된 약관에
                   재동의해야 계속 서비스를 이용할 수 있습니다.

                제4조 (서비스 이용)
                1. 이용자는 회사가 정한 방법에 따라 서비스를 이용할 수 있습니다.
                2. 이용자는 법령, 이 약관, 회사의 정책 등을 준수하여야 합니다.
                3. 회사는 서비스 운영상 필요한 경우 사전 공지 후 서비스를 중단할 수 있습니다.

                제5조 (이용자의 의무)
                1. 이용자는 본인의 계정 정보를 철저히 관리해야 합니다.
                2. 이용자는 타인의 계정을 도용하거나 부정하게 이용할 수 없습니다.
                3. 이용자는 불법적인 내용의 정보를 게시하거나 전송할 수 없습니다.

                제6조 (책임의 제한)
                회사는 다음 사항으로 인한 손해에 대해 책임을 지지 않습니다:
                1. 이용자의 귀책사유로 인한 손해
                2. 천재지변 또는 불가항력으로 인한 손해
                3. 기타 회사의 고의 또는 중과실이 없는 손해

                제7조 (분쟁 해결)
                이 약관과 관련한 분쟁은 한국 법을 준거법으로 하며,
                관할권은 회사의 본사 소재지 관할법원으로 합니다.

                부칙
                이 약관은 2026년 4월 10일부터 시행합니다.
                """;
    }

    /**
     * 개인정보보호정책 내용
     */
    private String getPrivacyPolicyContent() {
        return """
                ■ 개인정보보호정책

                제1조 (개인정보의 수집)
                회사는 다음과 같은 개인정보를 수집합니다:
                1. 수집 항목: 이름, 이메일, 휴대폰번호, 배송주소, 결제정보
                2. 수집 방법: 홈페이지, 모바일 앱, 전화, 이메일
                3. 수집 목적: 서비스 제공, 고객상담, 마케팅, 통계분석

                제2조 (개인정보의 이용)
                수집한 개인정보는 다음 목적으로만 이용합니다:
                1. 서비스 제공 및 요금 정산
                2. 고객 상담 및 불만 처리
                3. 신상품 및 이벤트 정보 제공 (동의한 경우만)
                4. 서비스 개선을 위한 통계 분석

                제3조 (개인정보의 제3자 제공)
                회사는 이용자의 개인정보를 다음의 경우를 제외하고 제3자에게 제공하지 않습니다:
                1. 배송을 위해 배송업체에 제공하는 경우
                2. 결제처리를 위해 결제사에 제공하는 경우
                3. 법령이 정하는 경우

                제4조 (개인정보의 보호)
                1. 회사는 개인정보를 안전하게 보호하기 위해 기술적, 관리적, 물리적 조치를 취합니다.
                2. 이용자의 비밀번호는 암호화되어 저장됩니다.
                3. 회사는 개인정보 보호를 위해 SSL 인증서를 사용합니다.

                제5조 (개인정보의 보유 및 이용기간)
                1. 개인정보는 수집/이용 목적이 달성될 때까지 보유합니다.
                2. 회원 탈퇴 시 법령에서 정한 기간을 제외하고 지체없이 파기합니다.

                제6조 (이용자의 권리)
                1. 이용자는 언제든 자신의 개인정보 조회 및 수정을 요청할 수 있습니다.
                2. 이용자는 언제든 개인정보 제공 동의를 철회할 수 있습니다.
                3. 이용자는 자신의 계정을 삭제할 수 있습니다.

                제7조 (개인정보 담당자)
                개인정보에 관한 문의사항은 다음으로 연락하실 수 있습니다:
                - 담당자: 개인정보보호담당자
                - 이메일: privacy@eumpetfood.com
                - 전화: 1234-5678

                부칙
                이 정책은 2026년 4월 10일부터 시행합니다.
                """;
    }

    /**
     * SMS 마케팅 동의 내용
     */
    private String getMarketingSmsContent() {
        return """
                ■ SMS 마케팅 정보 수신 동의

                이음펫푸드가 제공하는 다양한 상품 정보, 할인 정보, 이벤트 정보 등을 
                SMS를 통해 받으실 것에 동의하십니까?

                1. 수신 방법: 휴대폰 문자메시지(SMS)
                2. 수신 내용: 신상품 소개, 판매 촉진 이벤트, 특가 정보, 회사의 주요 공지사항
                3. 수신 빈도: 주 1~2회 정도 (상황에 따라 달라질 수 있음)

                동의 철회:
                - 언제든 회원정보 수정에서 동의를 철회할 수 있습니다.
                - 각 SMS의 하단의 거부 안내를 따를 수 있습니다.

                주의사항:
                - 필수 공지사항(구매 확인, 배송 안내 등)은 마케팅 거부 시에도 발송됩니다.
                - SMS 수신료는 귀사의 통신사 요금제에 따릅니다.
                """;
    }

    /**
     * 이메일 마케팅 동의 내용
     */
    private String getMarketingEmailContent() {
        return """
                ■ 이메일 마케팅 정보 수신 동의

                이음펫푸드가 제공하는 다양한 상품 정보, 할인 정보, 이벤트 정보 등을 
                이메일을 통해 받으실 것에 동의하십니까?

                1. 수신 방법: 등록하신 이메일 주소
                2. 수신 내용: 신상품 소개, 판매 촉진 이벤트, 특가 정보, 뉴스레터
                3. 수신 빈도: 주 1~3회 정도 (상황에 따라 달라질 수 있음)

                동의 철회:
                - 언제든 회원정보 수정에서 동의를 철회할 수 있습니다.
                - 각 이메일의 하단의 수신거부 링크를 클릭할 수 있습니다.

                주의사항:
                - 필수 공지사항(구매 확인, 배송 안내 등)은 마케팅 거부 시에도 발송됩니다.
                - 이메일이 스팸함으로 분류될 수 있으니 주소록에 등록해주시기 바랍니다.
                """;
    }
}