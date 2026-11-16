package dev.anas.redis;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.command.handlers.*;
import dev.anas.redis.server.Server;
import dev.anas.redis.store.ExpirySweeper;
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
        dispatcher.register("expire", new ExpireCommand(store));
        dispatcher.register("pexpire", new PexpireCommand(store));
        dispatcher.register("ttl", new TtlCommand(store));
        dispatcher.register("pttl", new PttlCommand(store));
        dispatcher.register("persist", new PersistCommand(store));
        dispatcher.register("incr", new IncrCommand(store));
        dispatcher.register("decr", new DecrCommand(store));
        dispatcher.register("incrby", new IncrByCommand(store));
        dispatcher.register("append", new AppendCommand(store));
        dispatcher.register("strlen", new StrlenCommand(store));
        dispatcher.register("lpush", new LpushCommand(store));
        dispatcher.register("rpush", new RpushCommand(store));
        dispatcher.register("lpop", new LpopCommand(store));
        dispatcher.register("rpop", new RpopCommand(store));
        dispatcher.register("llen", new LlenCommand(store));
        dispatcher.register("lrange", new LrangeCommand(store));

        ExpirySweeper sweeper = new ExpirySweeper(store, 100);
        sweeper.start();

        new Server(port, dispatcher).start();
    }
}