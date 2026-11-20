package dev.anas.redis.pubsub;

import dev.anas.redis.server.ClientHandler;
import dev.anas.redis.store.Key;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

public final class Broker {
    private final Map<Key, Set<ClientHandler>> subscriptions = new ConcurrentHashMap<>();
    private final Map<ClientHandler, Set<Key>> clientChannels = new ConcurrentHashMap<>();

    public synchronized int subscribe(ClientHandler client, byte[] channel) {
        subscriptions.computeIfAbsent(new Key(channel), k -> ConcurrentHashMap.newKeySet()).add(client);
        clientChannels.computeIfAbsent(client, k -> ConcurrentHashMap.newKeySet()).add(new Key(channel));

        return clientChannels.get(client).size();
    }

    public synchronized int unsubscribe(ClientHandler client, byte[] channel) {
        Key key = new Key(channel);

        Set<ClientHandler> subs = subscriptions.get(key);
        if (subs != null) {
            subs.remove(client);
            if (subs.isEmpty()) {
                subscriptions.remove(key);
            }
        }

        Set<Key> channels = clientChannels.get(client);
        if (channels != null) {
            channels.remove(key);
            if (channels.isEmpty()) {
                clientChannels.remove(client);
            }
        }

        return channels == null ? 0 : channels.size();
    }

    public synchronized Set<Key> unsubscribeAll(ClientHandler client) {
        Set<Key> channels = clientChannels.remove(client);
        if (channels == null)
            return Set.of();

        Set<Key> removed = Set.copyOf(channels);
        for (Key channel : removed) {
            Set<ClientHandler> subs = subscriptions.get(channel);
            if (subs != null) {
                subs.remove(client);
                if (subs.isEmpty())
                    subscriptions.remove(channel);
            }
        }
        return removed;
    }

    public int publish(byte[] channel, byte[] message) {
        Set<ClientHandler> subs = subscriptions.get(new Key(channel));
        if (subs == null || subs.isEmpty()) {
            return 0;
        }

        int receivers = 0;
        for (ClientHandler client : subs) {
            client.sendPubSubMessage(channel, message);
            receivers++;
        }
        return receivers;
    }

    public synchronized void removeClient(ClientHandler client) {
        Set<Key> channels = clientChannels.remove(client);
        if (channels != null) {
            for (Key channel : channels) {
                Set<ClientHandler> subs = subscriptions.get(channel);
                if (subs != null) {
                    subs.remove(client);
                    if (subs.isEmpty()) {
                        subscriptions.remove(channel);
                    }
                }
            }
        }
    }

    public Set<Key> getClientChannels(ClientHandler client) {
        Set<Key> channels = clientChannels.get(client);
        return channels == null ? Set.of() : Set.copyOf(channels);
    }
}