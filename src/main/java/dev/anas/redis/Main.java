package dev.anas.redis;

import java.io.IOException;
import java.util.Map;

import dev.anas.redis.command.Dispatcher;
import dev.anas.redis.command.handlers.*;
import dev.anas.redis.persistence.AofLoader;
import dev.anas.redis.persistence.AofWriter;
import dev.anas.redis.pubsub.Broker;
import dev.anas.redis.pubsub.handlers.PublishCommand;
import dev.anas.redis.pubsub.handlers.SubscribeCommand;
import dev.anas.redis.pubsub.handlers.UnsubscribeCommand;
import dev.anas.redis.server.Server;
import dev.anas.redis.store.ExpirySweeper;
import dev.anas.redis.store.Store;
import dev.anas.redis.pubsub.Command;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 6380;

        Store store = new Store();
        store.setMaxMemory(100L * 1024 * 1024);   // 100 MB, set 0 for unlimited
        AofWriter aof = new AofWriter("appendonly.aof");
        Dispatcher dispatcher = new Dispatcher(aof);

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
        dispatcher.register("hset", new HsetCommand(store));
        dispatcher.register("hget", new HgetCommand(store));
        dispatcher.register("hdel", new HdelCommand(store));
        dispatcher.register("hgetall", new HgetallCommand(store));
        dispatcher.register("hexists", new HexistsCommand(store));

        ExpirySweeper sweeper = new ExpirySweeper(store, 100);
        sweeper.start();

        AofLoader.replay("appendonly.aof", dispatcher);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                aof.close();
            } catch (IOException ignored) {
                // During JVM shutdown, close failure has nowhere to report. The process is
                // exiting.
            }
        }));

        Broker broker = new Broker();
        Map<String, Command> pubsub = Map.of(
                "subscribe", new SubscribeCommand(broker),
                "unsubscribe", new UnsubscribeCommand(broker),
                "publish", new PublishCommand(broker));
                
        new Server(port, dispatcher, pubsub).start();
    }
}