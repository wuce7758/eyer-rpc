package io.eyer.zookeeper;

import io.eyer.cluster.balance.PollingBalance;
import io.eyer.cluster.invoker.ZookeeperInvoker;
import io.eyer.nio.core.net.Connector;
import io.eyer.nio.core.net.ReactorPool;
import io.eyer.nio.core.support.Handler;
import io.eyer.nio.core.support.NioConfig;
import io.eyer.rpc.codec.KryoSerializer;
import io.eyer.rpc.handler.ClientHandler;
import io.eyer.rpc.invoker.Invoker;
import io.eyer.rpc.proxy.ProxyClient;
import io.eyer.service.User;
import io.eyer.service.UserService;

import java.util.concurrent.atomic.AtomicLong;

class Task extends Thread {
    private final UserService userService;
    private final AtomicLong counter;
    private final long startTime;
    private final long N;

    public Task(UserService userService, AtomicLong counter, long startTime, long N) {
        this.userService = userService;
        this.counter = counter;
        this.startTime = startTime;
        this.N = N;
    }

    @Override
    public void run() {
        for (int i = 0; i < N; i++) {

            try {
                userService.save(new User());
                counter.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (counter.get() % 5000 == 0) {
                double qps = counter.get() * 1000.0 / (System.currentTimeMillis() - startTime);
                System.out.format("QPS: %.2f\n", qps);
            }
        }
    }
}

public class ZookeeperProxyServicePerf {

    public static void main(String[] args) throws Exception {

        final long N = 1000000;
        final int threadCount = 50;

        NioConfig.registered(new KryoSerializer());
        final Handler invocationHandler = new ClientHandler();
        NioConfig.registered(invocationHandler);
        NioConfig.registered(new Connector("ZookeeperProxyServicePerf-connector-1",new ReactorPool(1)));
        NioConfig.initSessionHandler();


        Invoker invoker = new ZookeeperInvoker("192.168.1.66:2181",new PollingBalance(),50);

        ProxyClient proxyClient = new ProxyClient(invoker);


        UserService userService = proxyClient.refService(UserService.class);
        final AtomicLong counter = new AtomicLong(0);

        System.out.println("init success");
        final long startTime = System.currentTimeMillis();
        Task[] tasks = new Task[threadCount];
        for (int i = 0; i < threadCount; i++) {
            tasks[i] = new Task(userService, counter, startTime, N);
        }
        for (Task task : tasks) {
            task.start();
        }

    }
}
