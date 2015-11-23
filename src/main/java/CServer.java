import com.google.gson.Gson;
import java.util.regex.Matcher;
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
                registerEvent(event);


            if (event.type() == UNREGISTER)
                unregisterEvent(event);

            event.complete(true);
        });
    }

    private boolean sendEvent(BridgeEvent event)
    {
        log.info("Send event called.");
        String eventAddr = event.rawMessage().getString("address");
        String uuidRegEx = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

        log.info("Event address: " + eventAddr);

        //создание чата.
        if (Pattern.compile("com\\.server\\." + uuidRegEx).matcher(eventAddr).matches())
        {
            String uuidClient = eventAddr.substring(eventAddr.lastIndexOf('.') + 1);
            String json = event.rawMessage().getString("body");
            //log.info("JSON: " + json);
            CChat info = new Gson().fromJson(json, CChat.class);

            if (info.getFromId() == 0 || info.getToId() == 0)
            {
                log.info("FromId=" + info.getFromId() + ", ToId=" + info.getToId());
                return false;
            }

            //исключить возможность хака.
            String ip = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();
            //if (!CClient.getClient(info.getFromId()).getUuid().toString().equals(uuidClient))
            if (CClient.getClient(ip, port).getId() != info.getFromId())
            {
                log.info("Hacking");
                return false;
            }

            CChat thinChat = new CChat(info.getFromId(), info.getToId());
            //CClient fromClient = CClient.getClient(info.getFromId());
            int index = CClient.indexPrivateChat(thinChat);
            if (index == -1) CClient.addPrivateChat(thinChat);
            else thinChat = CClient.getPrivateChat(index); //unique uuid.


            CClient toClient = CClient.getClient(info.getToId());

            Map<String, Object> response = new TreeMap<String, Object>();
            response.put("thrown", true);
            response.put("chat", thinChat);
            vertx.eventBus().send("com.chat." + uuidClient, new Gson().toJson(response));
            //log.info("Send message to: com.chat." + uuidClient + "; Response: " + new Gson().toJson(response));
            response.put("thrown", false);
            vertx.eventBus().send("com.chat." + toClient.getUuid(), new Gson().toJson(response));
            //log.info("Send message to: com.chat." + toClient.getUuid() + "; Response: " + new Gson().toJson(response));

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

            CChat chat = CClient.getChatByAddress(uuidAddress);
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

        /*
        if (event.rawMessage().getString("address").equals("com.to.server"))
        {
            String json = event.rawMessage().getString("body");

            CComInfo info = new Gson().fromJson(json, CComInfo.class);
            if (info.fromId == 0 || info.toId == 0)
                return false;

            String host = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();

            CClient fromClient = CClient.getClient(info.fromId);

            if (fromClient == null)
                return false;

            if (fromClient.getHost().equals(host) && fromClient.getPort() == port)
            {
                CPrivateChat.statusChat status = fromClient.privateChat.addNewChat(info.fromId, info.toId);

                CChatInfo createdChat = fromClient.privateChat.getCreatedChatInfo(info.toId);
                CClient toClient = CClient.getClient(info.toId);
                CChatInfo invitedChat = null;
                if (toClient != null)
                    invitedChat = toClient.privateChat.getInvitedChatInfo(info.fromId);


                Map<String, Object> responseCreate = new HashMap<String, Object>();
                Map<String, Object> responseInvite = new HashMap<String, Object>();
                responseCreate.put("type", status);

                switch (status)
                {
                    case LIMIT_CREATED:
                        break;
                    case ALREADY_CREATED:
                        break;
                    case ALREADY_INVITE:
                        responseCreate.put("info", createdChat);
                        break;
                    case SUCCESS_CREATE:
                        responseCreate.put("info", createdChat);
                        responseInvite.put("type", CPrivateChat.statusChat.SUCCESS_INVITE);
                        responseInvite.put("info", invitedChat);
                        break;
                    case UNSECCESS:
                        break;
                    default:
                        log.warn("Something went wrong...");
                }

                log.info("JSON RESPONSE CREATE: " + new Gson().toJson(responseCreate).toString());
                if (status == CPrivateChat.statusChat.SUCCESS_CREATE)
                    vertx.eventBus().send(createdChat.address, new Gson().toJson(responseInvite));

                vertx.eventBus().send(fromClient.getUuid().toString(), new Gson().toJson(responseCreate));
            }
            else return false;

        }

        return false;*/



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

                    log.info("JSON PARMS: " + new Gson().toJson(parms));
                    vertx.eventBus().publish("chat.to.client", new Gson().toJson(parms));


                    Map<String, Object> parms2 = new HashMap<String, Object>(3);
                    parms2.put("type", "send");
                    parms2.put("client", client);
                    parms2.put("clients", CClient.getOnlineList());

                    log.info("JSON PARMS2: " + new Gson().toJson(parms2));
                    vertx.eventBus().send("data.on.chat", new Gson().toJson(parms2));
                    //TODO: заменить data.on.chat на con.chat.UUID.
                }
            }).start();
    }

    protected void unregisterEvent(BridgeEvent event)
    {
        if (event.rawMessage() == null)
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

                    Map<String, Object> parms = new HashMap<String, Object>(3);

                    parms.put("type", "unregister");
                    parms.put("online", CClient.getOnline());
                    parms.put("client", client);

                    log.info("Unregister handler: " + client.toString());

                    vertx.eventBus().publish("chat.to.client", new Gson().toJson(parms));
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
