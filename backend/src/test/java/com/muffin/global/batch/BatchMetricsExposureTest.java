package com.muffin.global.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 배치 지표가 실제로 스크레이프 가능한지 확인한다.
 *
 * <p>단위 테스트는 레지스트리에 값이 담기는 것까지만 보장한다. 그런데 이 경로는 <b>설정 문자열(노출 목록)과 보안 규칙</b>이라는, 컴파일이 잡아주지 않는 두 고리에
 * 걸려 있다. 둘 중 하나만 어긋나도 수집기는 401이나 404를 받고 대시보드와 알림이 통째로 비는데, 그 사실은 정작 장애가 났을 때 드러난다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BatchMetricsExposureTest {

    @LocalServerPort
    private int port;

    @Autowired
    private BatchJobMetrics metrics;

    @Test
    @DisplayName("수집기는 인증 없이 배치 지표를 긁어갈 수 있다")
    void prometheusEndpoint_servesBatchMetricsWithoutAuthentication() throws Exception {
        metrics.record(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BatchOutcome.SUCCESS, 1_240);

        HttpResponse<String> response = get("/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("muffin_batch_job_last_success_timestamp_seconds")
                .contains("batch_job=\"settlement\"")
                .contains("muffin_batch_job_duration_seconds_count");
    }

    /**
     * 잡 이름 라벨이 {@code job}으로 되돌아가면 수집기가 붙이는 {@code job="muffin-backend"}와 충돌해, 프로메테우스가 앱 쪽 값을
     * {@code exported_job}으로 밀어낸다. 대시보드와 알림 룰의 라벨 셀렉터가 통째로 빗나가는데 앱은 멀쩡히 200을 주므로, 렌더링된 본문에서 직접 막는다.
     */
    @Test
    @DisplayName("배치 지표는 예약 라벨인 job 대신 batch_job으로 잡 이름을 내보낸다")
    void prometheusEndpoint_doesNotEmitReservedJobLabel() throws Exception {
        metrics.record(BatchJob.SETTLEMENT, BatchTrigger.SCHEDULER, BatchOutcome.SUCCESS, 1_240);

        List<String> batchSamples = get("/actuator/prometheus")
                .body()
                .lines()
                .filter(line -> line.startsWith("muffin_batch_"))
                .toList();

        assertThat(batchSamples).isNotEmpty();
        assertThat(batchSamples).allSatisfy(line -> assertThat(line)
                .as("잡 이름은 batch_job으로만 나가야 한다")
                .contains("batch_job=\"")
                .doesNotContainPattern("[{,]job=\""));
    }

    @Test
    @DisplayName("노출 목록에 없는 액추에이터 엔드포인트는 열리지 않는다")
    void unlistedActuatorEndpoints_areNotExposed() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isNotEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
