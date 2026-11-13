package dev.anas.redis;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.command.handlers.*;
import dev.anas.redis.server.Server;
import dev.anas.redis.store.Store;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 6380;

        Store store = new Store();
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.register("ping", new PingCommand());
        dispatcher.register("echo", new EchoCommand());
        dispatcher.register("set", new SetCommand(store));
        dispatcher.register("get", new GetCommand(store));
        dispatcher.register("del", new DelCommand(store));
        dispatcher.register("exists", new ExistsCommand(store));

        new Server(port, dispatcher).start();
    }
}