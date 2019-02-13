package io.grpc.examples.routeguide;

import com.google.common.collect.Lists;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RouteGuideClient {
    // gRPC Channel 인터페이스 입니다. 통신 채널의 설정을 관리할 수 있습니다.
    private final ManagedChannel channel;

    // gRPC 클라이언트는 blocking/sync Stub과 non-blocking/async Stub이 있습니다.
    private final RouteGuideGrpc.RouteGuideBlockingStub blockingStub;
    private final RouteGuideGrpc.RouteGuideStub asyncStub;

    public RouteGuideClient(String host, int port) {
        // ManagedChannel은 ServiceProvider에 default 등록된 네트워크 프레임워크을 사용합니다.
        // 대개는 NettyChannelBuilder나 OkHttpChannelBuilder를 사용해서 지정하여 생성합니다.
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true)
                .build();
        // blocking/sync stub을 생성합니다.
        this.blockingStub = RouteGuideGrpc.newBlockingStub(channel);

        // non-blocking/async stub을 생성합니다.
        this.asyncStub = RouteGuideGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        // 클라이언트 사용이 완료된 후, 5초가 지나면 채널을 닫아줍니다.
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Simple RPC
     */
    public void getFeature(int lat, int lon) {
        // 서버에 보낼 요청 메시지를 생성합니다.
        Point request = Point.newBuilder().setLatitude(lat).setLongitude(lon).build();

        Feature feature;

        try {
            // blocking으로 서버의 getFeature() 메서드를 호출합니다.
            feature = blockingStub.getFeature(request);
        } catch (StatusRuntimeException e) {
            // gRPC 통신에 문제가 생기면 StatusRuntimeException이 발생합니다.
            return;
        }
    }

    /**
     * server-to-client streaming RPC
     */
    public List<Feature> listFeatures(int lowLat, int lowLon, int hiLat, int hiLon) {
        // 서버에 보낸 요청 메시지를 생성합니다.
        Rectangle request = Rectangle.newBuilder()
                .setLo(Point.newBuilder().setLatitude(lowLat).setLongitude(lowLon).build())
                .setHi(Point.newBuilder().setLatitude(hiLat).setLongitude(hiLon).build())
                .build();

        List<Feature> featureList;

        try {
            // blocking으로 서버의 listFeatures() 메서드를 호출하고 메시지 스트림을 받아서 리턴합니다.
            Iterator<Feature> features = blockingStub.listFeatures(request);
            featureList = Lists.newArrayList(features);
        } catch (StatusRuntimeException e) {
            // gRPC 통신에 문제가 생기면 StatusRuntimeException이 발생합니다.
            return null;
        }

        return featureList;
    }

    /**
     * client-to-server streaming RPC
     */
    public void recordRoute(List<Feature> features, int numPoints) throws InterruptedException {
        // 비동기 처리를 위해서 CountDownLatch를 사용하였습니다.
        final CountDownLatch finishLatch = new CountDownLatch(1);

        // 서버에서 보내는 메시지를 처리하기 위한 옵져버를 생성합니다.
        StreamObserver<RouteSummary> responseObserver =
            new StreamObserver<RouteSummary>() {
                @Override
                public void onNext(RouteSummary routeSummary) {
                    // 여기서는 양방향 스트림이 아니기때문에 정상적인 경우, onNext()가 한번씩만 호출됩니다.
                    log.info("A message received from server: {}", routeSummary);
                }

                @Override
                public void onError(Throwable throwable) {
                    // 서버에서 onError()를 호출하거나 내부 통신장애가 발생하면 호출됩니다.
                    log.error("RecordRoute Failed: {}", Status.fromThrowable(throwable));
                    finishLatch.countDown();
                }

                @Override
                public void onCompleted() {
                    // 메시지 전송이 완료되면 서버에서 onCompleted()를 호출합니다.
                    finishLatch.countDown();
                }
            };

        // non-blocking/async stub을 사용하여 서버의 recordRoute()를 호출하였습니다.
        // 서버의 응답을 기다리기 위해서 비동기 stub을 사용해야 합니다.
        StreamObserver<Point> requestObserver = asyncStub.recordRoute(responseObserver);

        try {
            for (int i = 0; i < numPoints; i++) {
                Point point = features.get(i).getLocation();

                // 클라이언트에서 서버로 메시지 스트림을 전송합니다.
                requestObserver.onNext(point);

                // 서버에서 종료하여 finishLatch.countDown()가 호출되면 종료합니다.
                if (finishLatch.getCount() == 0) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            // 메시지 전송과정에서 에러가 발생하면 onError()를 호출하여 서버에 에러 상황을 알립니다.
            requestObserver.onError(e);
            throw e;
        }

        // 메시지 전송이 완료되면 onCompleted()를 호출하여 서버에 종료 상황을 알립니다.
        requestObserver.onCompleted();

        // finishLatch를 사용하여 최대 1분간 blocking 하도록 합니다.
        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            log.warn("recordRoute can not finish within 1 minutes");
        }
    }

    public void routeChat() throws InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<RouteNote> requestObserver =
                asyncStub.routeChat(new StreamObserver<RouteNote>() {
                    @Override
                    public void onNext(RouteNote routeNote) {
                        // 서버로부터 오는 메시지 스트림을 처리합니다.
                        log.info("A message received from server: {}", routeNote);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // 서버에서 에러가 발생하면 호출합니다.
                        log.info("RouteChat Failed: {}", Status.fromThrowable(throwable));
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        // 서버에서 메시지 전송이 완료되면 호출합니다.
                        log.info("Finished RouteChat");
                        finishLatch.countDown();
                    }
                });

        try {
            RouteNote[] requests = {
                    RouteNote.newBuilder().setMessage("First").build(),
                    RouteNote.newBuilder().setMessage("Second").build()
            };

            for (RouteNote request: requests) {
                log.info("Sending message: {}", request);

                // 클라언트에서 서버로 메시지 스트림을 전송합니다.
                requestObserver.onNext(request);
            }
        } catch (RuntimeException e) {
            // 에러가 발생하면 서버에 에러 상황을 알립니다.
            requestObserver.onError(e);
            throw e;
        }

        // 메시지 전송이 끝나면 서버에 종료 상황을 알립니다.
        requestObserver.onCompleted();
        log.info("Call requestObserver.onCompleted();");

        // finishLatch를 사용하여 최대 1분간 blocking 하도록 합니다.
        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            log.warn("recordChat can not finish within 1 minutes");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // 클라이언트를 생성합니다. 이때 서버의 주소와 포트번호를 입력합니다.
        RouteGuideClient client = new RouteGuideClient("localhost", 8980);

        try {
            // 클라이언트의 메서드를 호출하여 서버의 메서드들을 테스트합니다.
            client.getFeature(409146138, -746188906);
            List<Feature> featureList = client.listFeatures(400000000, -750000000, 420000000, -730000000);
            client.recordRoute(featureList, featureList.size());
            client.routeChat();
        } finally {
            // 클라이언트 사용이 완료된 후, 채널을 종료합니다.
            client.shutdown();
        }
    }
}
