import com.google.gson.Gson;

import java.util.regex.Pattern;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Starter;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.*;

import static io.vertx.ext.web.handler.sockjs.BridgeEvent.Type.*;

public class CServer extends AbstractVerticle
{
    private Logger log = LoggerFactory.getLogger(CServer.class);

    private SockJSHandler mHandler = null;

    @Override
    public void start() throws Exception
    {
        if (!deploy())
        {
            log.error("Failed to deploy the server.");
            return;
        }

        handle();
    }

    private boolean deploy()
    {
        int hostPort = getFreePort();

        if (hostPort < 0)
            return false;

        Router router = Router.router(vertx);

        //мост шины событий.
        mHandler = SockJSHandler.create(vertx);

        router.route("/eventbus/*").handler(mHandler);
        router.route().handler(StaticHandler.create());

        //запуск веб-сервера.
        vertx.createHttpServer().requestHandler(router::accept).listen(hostPort);

        try
        {
            String addr = InetAddress.getLocalHost().getHostAddress();
            log.info("Access to \"twitterok\" at the following address: \nhttp://" + addr + ":" + hostPort);
        }
        catch (UnknownHostException e)
        {
            log.error("Failed to get the local address: [" + e.toString() + "]");
            return false;
        }

        return true;
    }

    private int getFreePort(int hostPort)
    {
        try
        {
            ServerSocket socket = new ServerSocket(hostPort);
            int port = socket.getLocalPort();
            socket.close();

            return port;
        }
        catch (BindException e)
        {
            if (hostPort != 0)
                return getFreePort(0);

            log.error("Failed to get the free port: [" + e.toString() + "]");
            return -1;
        }
        catch (IOException e)
        {
            log.error("Failed to get the free port: [" + e.toString() + "]");
            return -1;
        }
    }

    private int getFreePort()
    {
        int hostPort = 8080;

        if (Starter.PROCESS_ARGS != null
                && Starter.PROCESS_ARGS.size() > 0)
        {
            try
            {
                hostPort = Integer.valueOf(Starter.PROCESS_ARGS.get(0));
            } catch (NumberFormatException e)
            {
                log.warn("Invalid port: [" + Starter.PROCESS_ARGS.get(0) + "]");
            }
        }

        if (hostPort < 0 || hostPort > 65535)
            hostPort = 8080;

        return getFreePort(hostPort);
    }

    private void handle()
    {
        String uuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("chat.to.server"))
                .addOutboundPermitted(new PermittedOptions().setAddress("chat.to.client"))
                .addInboundPermitted(new PermittedOptions().setAddressRegex("private\\.server\\." + uuidRegex))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex("private\\.chat\\." + uuidRegex))
                .addInboundPermitted(new PermittedOptions().setAddressRegex("com\\.server\\." + uuidRegex))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex("com\\.chat\\." + uuidRegex))
                .addOutboundPermitted(new PermittedOptions().setAddress("data.on.chat"));

        mHandler.bridge(opts, event ->
        {
            if (event.type() == PUBLISH)
                publishEvent(event);

            if (event.type() == SEND)
                sendEvent(event);

            if (event.type() == REGISTER)
                registerEvent(event);

            if (event.type() == SOCKET_CLOSED)
                closeEvent(event);

            event.complete(true);
        });
    }

    //обработчик публичных твитов.
    private boolean publishEvent(BridgeEvent event)
    {
        if (event.rawMessage() != null
            && event.rawMessage().getString("address").equals("chat.to.server"))
        {
            String message = event.rawMessage().getString("body");
            if (!verifyMessage(message))
                return false;

            String host = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();

            Map<String, Object> publicNotice = createPublicNotice(host, port, message);
            vertx.eventBus().publish("chat.to.client", new Gson().toJson(publicNotice));
            return true;
        }
        else
            return false;
    }

    //события с приватными чатами.
    private boolean sendEvent(BridgeEvent event)
    {
        String eventAddr = event.rawMessage().getString("address");
        String uuidRegEx = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

        //создание приватного чата.
        if (Pattern.compile("com\\.server\\." + uuidRegEx).matcher(eventAddr).matches())
            return createPrivateChat(event);

        //отправление сообщения в приватный чат.
        return Pattern.compile("private\\.server\\." + uuidRegEx).matcher(eventAddr).matches()
                && privateTweet(event);

    }

    //обработчик событий создания сессии.
    private void registerEvent(BridgeEvent event)
    {
        if (event.rawMessage() != null
            &&event.rawMessage().getString("address").equals("chat.to.client"))
            new Thread(() ->
            {
                String host = event.socket().remoteAddress().host();
                int port = event.socket().remoteAddress().port();
                Date time = Calendar.getInstance().getTime();

                CClient client = new CClient(host, port, time);

                Map<String, Object> registerNotice = createRegisterNotice(client);
                vertx.eventBus().publish("chat.to.client", new Gson().toJson(registerNotice));

                Map<String, Object> dataNotice = createDataNotice(client);
                vertx.eventBus().send("data.on.chat", new Gson().toJson(dataNotice));
            }).start();
    }

    //обработчик событий завершения сессии.
    private void closeEvent(BridgeEvent event)
    {
        new Thread(() ->
        {
            String host = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();

            CClient client = CClient.unregisterClient(host, port);
            if (client == null) //клиент не был зарегестрирован.
            {
                log.warn("Client with the address [" + host + ":" + port + "] has not been registered.");
                return;
            }

            //список закрывшихся приватных чатов.
            List<UUID> closedAddress = CClient.closePrivateChat(client.getId());
            //информирование в приватные чаты, что собеседник покинул чат.
            for (UUID address : closedAddress)
            {
                Map<String, Object> closedNotice = createClosedNotice(client, host, port);
                vertx.eventBus().send("private.chat." + address.toString(), new Gson().toJson(closedNotice));
            }

            //информирование в чат.
            Map<String, Object> infoNotice = createInfoNotice(client);
            vertx.eventBus().publish("chat.to.client", new Gson().toJson(infoNotice));
        }).start();
    }

    //создание приватного чата.
    private boolean createPrivateChat(BridgeEvent event)
    {
        //проверка полученного события.
        if (!verifyEvent(event))
            return false;

        String jMsg = event.rawMessage().getString("body");
        CChatInfo chatInfo = new Gson().fromJson(jMsg, CChatInfo.class);
        chatInfo.genAddress();

        int index = CClient.indexPrivateChat(chatInfo);
        if (index == -1) CClient.addPrivateChat(chatInfo);
        else chatInfo = CClient.getPrivateChat(index); //уникальный uuid.

        //чат активен.
        if (!isOpenPrivateChat(chatInfo))
            return false;

        CClient fromIdClient = CClient.getClient(chatInfo.getFromId());
        CClient toIdClient = CClient.getClient(chatInfo.getToId());

        //клиенты зарегистрированы.
        if (!isRegisteredClients(fromIdClient, toIdClient))
            return false;

        String fromIdAddress = fromIdClient.getUuid().toString();
        String toIdAddress = toIdClient.getUuid().toString();

        log.debug("Send chat info to: com.chat." + fromIdAddress + ", message: " + new Gson().toJson(chatInfo));
        log.debug("Send chat info to: com.chat." + toIdAddress + ", message: " + new Gson().toJson(chatInfo));

        vertx.eventBus().send("com.chat." + fromIdAddress, new Gson().toJson(chatInfo));
        vertx.eventBus().send("com.chat." + toIdAddress, new Gson().toJson(chatInfo));

        return true;
    }

    //приватный твит.
    private boolean privateTweet(BridgeEvent event)
    {
        String eventAddr = event.rawMessage().getString("address");
        String uuidAddress = eventAddr.substring(eventAddr.lastIndexOf('.') + 1);
        String message = event.rawMessage().getString("body");

        if (!verifyMessage(message))
        {
            log.warn("The message isn't verify.");
            return false;
        }

        String host = event.socket().remoteAddress().host();
        int port = event.socket().remoteAddress().port();
        CChatInfo chatInfo = CClient.getPrivateChatByUuid(uuidAddress);

        //отсутствует приватный чат с указанным адресом.
        if (!isOpenPrivateChat(chatInfo))
            return false;

        Map<String, Object> privateTweetNotice = createPrivateTweetNotice(chatInfo, host, port, message);
        vertx.eventBus().publish("private.chat." + uuidAddress, new Gson().toJson(privateTweetNotice));

        return true;
    }

    //создание уведомления публикации твита.
    private Map<String, Object> createPublicNotice(String host, int port, String message)
    {
        Date time = Calendar.getInstance().getTime();

        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "publish");
        notice.put("time", time.toString());
        notice.put("host", host);
        notice.put("port", port);
        notice.put("message", message);
        return notice;
    }

    //создание уведомления отправления приватного твита.
    private Map<String, Object> createPrivateTweetNotice(CChatInfo chatInfo, String host, int port, String message)
    {
        Date time = Calendar.getInstance().getTime();

        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "send");
        notice.put("time", time.toString());
        notice.put("host", host);
        notice.put("port", port);
        notice.put("message", message);
        notice.put("toId", chatInfo.getToId());
        notice.put("fromId", chatInfo.getFromId());
        return notice;
    }

    //создание уведомления о регистрации.
    private Map<String, Object> createRegisterNotice(CClient client)
    {
        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "register");
        notice.put("client", client);
        notice.put("uuid", client.getUuid().toString());
        notice.put("online", CClient.getOnline());
        return notice;
    }

    //создание уведомления с данными.
    private Map<String, Object> createDataNotice(CClient client)
    {
        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "send");
        notice.put("client", client);
        notice.put("clients", CClient.getOnlineList());
        return notice;
    }

    //создание уведомления о закрытии чата.
    private Map<String, Object> createClosedNotice(CClient client, String host, int port)
    {
        Date time = Calendar.getInstance().getTime();

        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "close");
        notice.put("time", time.toString());
        notice.put("host", host);
        notice.put("port", port);
        notice.put("toId", client.getId());
        return notice;
    }

    //создание уведомления в чат.
    private Map<String, Object> createInfoNotice(CClient client)
    {
        Map<String, Object> notice = new TreeMap<>();
        notice.put("type", "close");
        notice.put("online", CClient.getOnline());
        notice.put("client", client);
        return notice;
    }

    //оба клиента зарегистированы в приватном чате.
    private boolean isRegisteredClients(CClient fromIdClient, CClient toIdClient)
    {
        if (fromIdClient == null || toIdClient == null)
        {
            log.warn("FromId Client or ToId Client isn't registered.");
            return false;
        }
        return true;
    }

    //чат активен.
    private boolean isOpenPrivateChat(CChatInfo chatInfo)
    {
        if (chatInfo == null)
        {
            log.warn("Chat does not exist or has been closed.");
            return false;
        }
        return true;
    }

    //верификация принятого сообщения.
    private boolean verifyMessage(String msg)
    {
        return  msg.length() > 0
                && msg.length() <= 140;
    }

    //проверка полученного события.
    private boolean verifyEvent(BridgeEvent event)
    {
        String jMsg = event.rawMessage().getString("body");
        CChatInfo chatInfo = new Gson().fromJson(jMsg, CChatInfo.class);

        return verifyReceivedChatInfo(chatInfo) && verifySender(event, chatInfo);
    }

    //проверка полученной информации.
    private boolean verifyReceivedChatInfo(CChatInfo chatInfo)
    {
        if (chatInfo.getFromId() == 0 || chatInfo.getToId() == 0)
        {
            log.warn("FromId=" + chatInfo.getFromId() + ", ToId=" + chatInfo.getToId());
            return false;
        }
        return true;
    }

    //проверка отправителя.
    private boolean verifySender(BridgeEvent event, CChatInfo chatInfo)
    {
        //исключить возможность хака.
        String ip = event.socket().remoteAddress().host();
        int port = event.socket().remoteAddress().port();
        CClient fromClient = CClient.getClient(ip, port);
        if (fromClient != null && fromClient.getId() != chatInfo.getFromId())
        {
            log.warn("Server FromID differs from the received FromID.");
            return false;
        }
        return true;
    }
}