package io.grpc.examples.routeguide;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class RouteGuideServer {
    private final int port;
    private final Server server;

    public RouteGuideServer(int port) throws IOException {
        this.port = port;
        // io.grpc.ServerBuilder를 사용하여 코드를 설정하고, 서비스를 추가합니다.
        this.server = ServerBuilder.forPort(port)
                .addService(new RouteGuideService())
                .build();
    }

    public void start() throws IOException {
        server.start(); // 서버를 시작합니다.

        // 서버가 SIGTERM을 받았을때 종료하기 위한 shutdown hook을 추가합니다.
        Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    RouteGuideServer.this.stop(); // SIGTERM을 받으면 서버를 종료합니다.
            }
        });
    }

    public void stop() {
        if (server != null) {
            server.shutdown();  // 서버를 종료합니다.
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();  // 서버가 SIGTERM을 받아서 종료될 수 있도록 await 합니다.
        }
    }

    public static void main(String[] args) throws Exception {
        // 8980 포트로 서버를 생성합니다.
        RouteGuideServer server = new RouteGuideServer(8980);
        server.start();                 // 서버를 시작합니다.
        server.blockUntilShutdown();    // 서버가 데몬으로 실행될 수 있도록 블럭합니다.
    }
}
