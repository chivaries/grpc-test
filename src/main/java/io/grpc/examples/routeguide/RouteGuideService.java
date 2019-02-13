package io.grpc.examples.routeguide;

import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RouteGuideService extends RouteGuideGrpc.RouteGuideImplBase{
    /**
     * proto에 정의된 getFeature() 함수입니다. (Simple RPC)
     * Point 메시지는 proto에 정의된 타입으로 자동 생성되어 입력으로 들어옵니다.
     * StreamObserver<Feature>는 응답(Feature)에 대한 옵져버 인터페이스 입니다.
     */
    @Override
    public void getFeature(Point request, StreamObserver<Feature> responseObserver) {
        // 자동 생성된 Feature은 기본적으로 빌더 패턴으로 객체를 만들 수 있도록 코드가 생성됩니다.
        Feature feature = Feature.newBuilder().setName("").setLocation(request).build();

        // 서버에서 onNext()를 호출하여 Feature 메시지를 클라이언트 보냅니다.
        responseObserver.onNext(feature);

        // 서버에서 onCompleted()를 호출하여 메시지 전송이 끝났음을 알립니다.
        responseObserver.onCompleted();
    }

    /**
     * server-to-client streaming RPC
     * 함수의 프로토타입은 Simple RPC와 차이가 없습니다.
     * 하지만, 서버에서 클라이언트로 여러개의 Feature 메시지를 보낼 수 있습니다.
     */
    @Override
    public void listFeatures(Rectangle request, StreamObserver<Feature> responseObserver) {
        for (int i = 0; i < 10; i++) {
            Feature feature = Feature.newBuilder()
                    .setLocation(request.getLo())
                    .setName(String.valueOf(i))
                    .build();

            // 10개의 Feature 메시지 스트림을 클라이언트에 하나씩 전송합니다.
            responseObserver.onNext(feature);
        }

        responseObserver.onCompleted();
    }

    /**
     * client-to-server streaming RPC
     * 입력 파라메터로는 클라이언트에 응답을 보내기위한 StreamObserver 인터페이스만 있습니다.
     * 리턴 타입은 클라이언트에서 보내는 메시지 스트림을 핸들링하는 StreamObserver로
     * 함수 구현부에서 바로 인스턴스화 합니다.
     */
    @Override
    public StreamObserver<Point> recordRoute(final StreamObserver<RouteSummary> responseObserver) {
        return new StreamObserver<Point>() {
            int pointCount;

            // 클라이언트에서 onNext(point)를 호출할때마다 실행됩니다.
            @Override
            public void onNext(Point point) {
                // 클라이언트에서 보낸 Point가 들어옵니다.
                pointCount++;
                log.info("Point message received {}, [pointCount : {}]", point, pointCount);
            }

            // 클라이언트에서 onError()를 호출하거나, 내부 에러가 발생하면 호출됩니다.
            @Override
            public void onError(Throwable throwable) {
                log.warn("recordRoute cancelled");
            }

            // 클라이언트에서 모든 메시지 스트림 전송을 마치고 onComplete()를 호출하면 실행됩니다.
            // 클라이언트는 onComplete()를 호출한 후, 응답(RouteSummary)가 리턴될때까지 기다립니다.
            @Override
            public void onCompleted() {
                RouteSummary summary = RouteSummary.newBuilder()
                    .setPointCount(pointCount)
                    .build();

                responseObserver.onNext(summary); //서버는 클라이언트에 응답을 보내고
                responseObserver.onCompleted();   // onCompleted() 를 호출하여 통신을 종료합니다.
            }
        };
    }

    /**
     * Bidirectional streaming RPC
     * 서버와 클라이언트가 독립적인 채널로 메시지 스트림을 보내고 받습니다.
     * 함수의 프로토타입은 client-to-server streaming RPC와 동일합니다.
     */
    @Override
    public StreamObserver<RouteNote> routeChat(final StreamObserver<RouteNote> responseObserver) {
        return new StreamObserver<RouteNote>() {
            // 클라이언트에서 onNext(note)로 메시지를 보낼때마다 호출됩니다.
            @Override
            public void onNext(RouteNote routeNote) {
                for (int i = 0; i < 10; i++) {
                    log.info("Send message : {}", routeNote);
                    // 클라이언트의 메시지를 전송중에도 서버에서는 클라이언트의 onNext(note)를 호출하여
                    // 메시지 스트림을 보낼 수 있습니다. 서버와 클라이언트 각각 코드에 작성된 순서대로
                    // 메시지를 가져오지만 순서에 상관없이 읽고 쓸 수 있습니다.
                    // 즉, 스트림은 완전히 독립적으로 작동합니다.
                    responseObserver.onNext(RouteNote.newBuilder(routeNote).build());
                } 
            }

            // 클라이언트에서 onError()를 호출하거나, 내부 에러가 발생하면 호출됩니다.
            @Override
            public void onError(Throwable throwable) {
                log.warn("routeChat cancelled");
            }

            @Override
            public void onCompleted() {
                // 서버에서도 onCompleted()를 호출하여 스트리밍 종료를 알려줍니다.
                log.info("server onCompleted!");
                responseObserver.onCompleted();
            }
        };
    }
}
