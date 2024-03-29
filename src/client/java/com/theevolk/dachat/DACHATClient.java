package com.theevolk.dachat;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theevolk.dachat.config.ModConfig;
import com.theevolk.dachat.donationalerts.DaMessage;

import io.github.centrifugal.centrifuge.Client;
import io.github.centrifugal.centrifuge.ConnectEvent;
import io.github.centrifugal.centrifuge.DisconnectEvent;
import io.github.centrifugal.centrifuge.EventListener;
import io.github.centrifugal.centrifuge.Options;
import io.github.centrifugal.centrifuge.PublishEvent;
import io.github.centrifugal.centrifuge.SubscribeErrorEvent;
import io.github.centrifugal.centrifuge.SubscribeSuccessEvent;
import io.github.centrifugal.centrifuge.Subscription;
import io.github.centrifugal.centrifuge.SubscriptionEventListener;
import io.github.centrifugal.centrifuge.UnsubscribeEvent;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class DACHATClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("dachat");
	private Client client;

	@Override
	public void onInitializeClient() {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        ModConfig config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

        if (config.token == "") {
            LOGGER.error("No DA token provided.");
            return;
        }

		LOGGER.info("Connecting to chat widget...");

		EventListener listener = new EventListener() {
            @Override
            public void onConnect(Client client, ConnectEvent event) {
                LOGGER.info("connected with client id " + event.getClient());
            }

            @Override
            public void onDisconnect(Client client, DisconnectEvent event) {
                LOGGER.error("disconnected " + event.getReason() + ", reconnect " + event.getReconnect());
            }
        };

        client = new Client("wss://api-chat.donationalerts.com/ws?format=protobuf", new Options(), listener);

        client.setToken(config.token);
        client.connect();

        String[] tokenParts = config.token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(tokenParts[1]));
        JsonObject payloadJson = JsonParser.parseString(payload).getAsJsonObject();
        String subject = payloadJson.get("sub").getAsString();
		subscribeToChat(subject);
    }

	private void subscribeToChat(String id) {
		Gson gson = new Gson();

        SubscriptionEventListener subListener = new SubscriptionEventListener() {
            @Override
            public void onSubscribeSuccess(Subscription sub, SubscribeSuccessEvent event) {
                LOGGER.info("subscribed to " + sub.getChannel());
            }

            @Override
            public void onSubscribeError(Subscription sub, SubscribeErrorEvent event) {
                LOGGER.error("subscribe error " + sub.getChannel() + " " + event.getMessage());
            }

            @Override
            public void onPublish(Subscription sub, PublishEvent event) {
                String data = new String(event.getData(), UTF_8);
                LOGGER.info("message from " + sub.getChannel() + " " + data);

				@SuppressWarnings("resource")
				var player = MinecraftClient.getInstance().player;
				if (player == null) return;

				DaMessage message = gson.fromJson(data, DaMessage.class);
				player.sendMessage(Text.literal(String.format("[%s] %s: %s", message.data.platform, message.data.user, message.data.payload)));
            }

            @Override
            public void onUnsubscribe(Subscription sub, UnsubscribeEvent event) {
                LOGGER.warn("unsubscribed " + sub.getChannel());
            }
        };

        Subscription sub = client.newSubscription("user#" + id, subListener);
        sub.subscribe();
	}
}