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

    protected boolean deploy()
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

    protected void handle()
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

        mHandler.bridge(opts, event -> {
            if (event.type() == PUBLISH)
                publishEvent(event);

            if (event.type() == SEND)
                sendEvent(event);

            if (event.type() == REGISTER)
            //if (event.type() == SOCKET_CREATED)
                registerEvent(event);


            //if (event.type() == UNREGISTER)
            if (event.type() == SOCKET_CLOSED)
                unregisterEvent(event);

            event.complete(true);
        });
    }

    private boolean sendEvent(BridgeEvent event)
    {
        log.info("Send event called, message: " + event.rawMessage().toString());

        String eventAddr = event.rawMessage().getString("address");
        String uuidRegEx = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

        //log.info("Event address: " + eventAddr);

        //создание чата.
        if (Pattern.compile("com\\.server\\." + uuidRegEx).matcher(eventAddr).matches())
        {
            String uuidClient = eventAddr.substring(eventAddr.lastIndexOf('.') + 1);
            String json = event.rawMessage().getString("body");
            //log.info("JSON: " + json);
            CChatInfo chatInfo = new Gson().fromJson(json, CChatInfo.class);
            chatInfo.genAddress();

            if (chatInfo.getFromId() == 0 || chatInfo.getToId() == 0)
            {
                log.info("FromId=" + chatInfo.getFromId() + ", ToId=" + chatInfo.getToId());
                return false;
            }

            //исключить возможность хака.
            String ip = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();
            //if (!CClient.getClient(info.getFromId()).getUuid().toString().equals(uuidClient))
            if (CClient.getClient(ip, port).getId() != chatInfo.getFromId())
            {
                log.info("Hacking");
                return false;
            }

            //CChatInfo thinChat = new CChatInfo(chatInfo.getFromId(), chatInfo.getToId());
            log.info("Chat Info: " + chatInfo.toString());
            //CClient fromClient = CClient.getClient(info.getFromId());
            int index = CClient.indexPrivateChat(chatInfo);
            if (index == -1) CClient.addPrivateChat(chatInfo);
            else chatInfo = CClient.getPrivateChat(index); //unique uuid.

            log.info("Chat Info index: " + index);
            log.info("Chat Info after: " + chatInfo.toString());


            CClient toClient = CClient.getClient(chatInfo.getToId());

            //Map<String, Object> response = new TreeMap<String, Object>();
            //response.put("thrown", true);
            //response.put("chat", chatInfo);

            String fromIdAddress = CClient.getClient(chatInfo.getFromId()).getUuid().toString();
            String toIdAddress = CClient.getClient(chatInfo.getToId()).getUuid().toString();

            log.info("Send chat info to: com.chat." + fromIdAddress + ", message: " + new Gson().toJson(chatInfo));
            log.info("Send chat info to: com.chat." + toIdAddress + ", message: " + new Gson().toJson(chatInfo));

            vertx.eventBus().send("com.chat." + fromIdAddress, new Gson().toJson(chatInfo));
            vertx.eventBus().send("com.chat." + toIdAddress, new Gson().toJson(chatInfo));


            /*
            vertx.eventBus().send("com.chat." + uuidClient, new Gson().toJson(response));
            //log.info("Send message to: com.chat." + uuidClient + "; Response: " + new Gson().toJson(response));
            //response.put("thrown", false);
            vertx.eventBus().send("com.chat." + toClient.getUuid(), new Gson().toJson(response));
            //log.info("Send message to: com.chat." + toClient.getUuid() + "; Response: " + new Gson().toJson(response));
            */
            return true;
        }

        //отправление сообщения в приватный чат.
        //if (eventAddr.startsWith("private\\.server\\." + uuidRegEx))
        if (Pattern.compile("private\\.server\\." + uuidRegEx).matcher(eventAddr).matches())
        {
            //TODO: мб добавить проверку uuid, от хака.
            String uuidAddress = eventAddr.substring(eventAddr.lastIndexOf('.') + 1);
            String message = event.rawMessage().getString("body");
            //log.info("Get Message: " + message);
            if (!verifyMessage(message))
                return false;

            String ip = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();

            CClient fromClient = CClient.getClient(ip, port);

            CChatInfo chat = CClient.getChatByAddress(uuidAddress);
            if (chat == null)
            {
                log.info("There is no a chat with the address: " + uuidAddress);
                return false;
            }

            String time = Calendar.getInstance().getTime().toString();

            Map<String, Object> parms = new TreeMap<String, Object>();
            parms.put("type", "send");
            parms.put("time", time);
            parms.put("host", ip);
            parms.put("port", port);
            parms.put("message", message);
            parms.put("toId", chat.getToId());
            parms.put("fromId", chat.getFromId());

            vertx.eventBus().publish("private.chat." + uuidAddress, new Gson().toJson(parms));
            return true;
        }

        return false;
    }

    protected boolean publishEvent(BridgeEvent event)
    {
        if (event.rawMessage().getString("address").equals("chat.to.server"))
        {
            String message = event.rawMessage().getString("body");
            if (!verifyMessage(message))
                return false;

            String ip = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();

            String time = Calendar.getInstance().getTime().toString();

            Map<String, Object> parms = new TreeMap<String, Object>();
            parms.put("type", "publish");
            parms.put("time", time);
            parms.put("host", ip);
            parms.put("port", port);
            parms.put("message", message);

            log.debug("Publish, host: " + ip + ", port: " + port);

            vertx.eventBus().publish("chat.to.client", new Gson().toJson(parms));
            return true;
        }
        else
            return false;
    }

    protected void registerEvent(BridgeEvent event)
    {
        if (event.rawMessage().getString("address").equals("chat.to.client"))
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    String host = event.socket().remoteAddress().host();
                    int port = event.socket().remoteAddress().port();
                    Date time = Calendar.getInstance().getTime();

                    CClient client = new CClient(host, port, time);
                    Map<String, Object> parms = new HashMap<String, Object>(3);

                    parms.put("type", "register");
                    parms.put("client", client);
                    parms.put("uuid", client.getUuid());
                    parms.put("online", CClient.getOnline());

                    //log.info("JSON PARMS: " + new Gson().toJson(parms));
                    vertx.eventBus().publish("chat.to.client", new Gson().toJson(parms));


                    Map<String, Object> parms2 = new HashMap<String, Object>(3);
                    parms2.put("type", "send");
                    parms2.put("client", client);
                    parms2.put("clients", CClient.getOnlineList());

                    //log.info("JSON PARMS2: " + new Gson().toJson(parms2));
                    vertx.eventBus().send("data.on.chat", new Gson().toJson(parms2));
                    //TODO: заменить data.on.chat на con.chat.UUID.
                }
            }).start();
    }

    protected void unregisterEvent(BridgeEvent event)
    {
        if (event.rawMessage() != null)
        {
            log.info("RawMessage: " + event.rawMessage());
        }
        else
            log.info("Event is null");

        //if (event.rawMessage() == null)
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    String host = event.socket().remoteAddress().host();
                    int port = event.socket().remoteAddress().port();

                    CClient client = CClient.unregisterClient(host, port);
                    if (client == null)
                        return;

                    List<String> closedAddress = CClient.closeChat(client.getId());
                    String time = Calendar.getInstance().getTime().toString();

                    for (String address : closedAddress)
                    {
                        Map<String, Object> notices = new TreeMap<String, Object>();
                        notices.put("type", "unregister");
                        notices.put("time", time);
                        notices.put("host", host);
                        notices.put("port", port);
                        //notices.put("message", "Собеседник покинул чат");
                        notices.put("toId", client.getId());
                        vertx.eventBus().publish("private.chat." + address, new Gson().toJson(notices));
                    }

                    Map<String, Object> notice = new TreeMap<String, Object>();
                    notice.put("type", "unregister");
                    notice.put("online", CClient.getOnline());
                    notice.put("client", client);
                    log.info("Unregister handler: " + client.toString());
                    vertx.eventBus().publish("chat.to.client", new Gson().toJson(notice));
                }
            }).start();
    }

    protected boolean verifyMessage(String msg)
    {
        if (msg.length() < 1 || msg.length() > 140)
            return false;
        else
            return true;
    }
}
