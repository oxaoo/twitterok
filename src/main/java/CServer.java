import com.google.gson.Gson;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
                .addOutboundPermitted(new PermittedOptions().setAddress("data.on.chat"))
                .addInboundPermitted(new PermittedOptions().setAddressRegex(uuidRegex))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex(uuidRegex))
                .addInboundPermitted(new PermittedOptions().setAddress("com.to.server"));

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

            if (fromClient.getHost() == host && fromClient.getPort() == port)
            {
                boolean isCreate = fromClient.privateChat.createNewChat(info.fromId, info.toId);
                if (!isCreate)
                {
                    log.info("Failed create new private chat");
                    return false;
                }

                //fromClient.privateChat.getCreatedChatInfo(info.toId);
                CChatInfo createdChat = fromClient.privateChat.getCreatedChatInfo(info.toId);
                CChatInfo invatedChat = CClient.getClient(info.toId).privateChat.getInvatedChatInfo(info.fromId);


                if (createdChat == null || invatedChat == null)
                {
                    log.info("Created Chat or Invated Chat is NULL");
                    return false;
                }

                log.info("JSON Create Chat: " + new Gson().toJson(createdChat));
                log.info("JSON Invate Chat: " + new Gson().toJson(invatedChat));

                vertx.eventBus().send(createdChat.address, new Gson().toJson(invatedChat));

                vertx.eventBus().send(fromClient.getUuid().toString(), new Gson().toJson(createdChat));
            }
            else return false;

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

            Gson gson = new Gson();
            String ip = event.socket().remoteAddress().host();
            int port = event.socket().remoteAddress().port();

            String time = Calendar.getInstance().getTime().toString();

            Map<String, Object> parms = new HashMap<String, Object>(5);
            parms.put("type", "publish");
            parms.put("time", time);
            parms.put("host", ip);
            parms.put("port", port);
            parms.put("message", message);

            log.debug("Publish, host: " + ip + ", port: " + port);

            vertx.eventBus().publish("chat.to.client", gson.toJson(parms));
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

                    Map<String, Object> parms = new HashMap<String, Object>(3);
                    CClient client = new CClient(host, port, time);
                    parms.put("type", "register");
                    parms.put("client", client);
                    parms.put("uuid", client.getUuid().toString());
                    parms.put("online", CClient.getOnline());

                    log.info("JSON PARMS: " + new Gson().toJson(parms));

                    vertx.eventBus().publish("chat.to.client", new Gson().toJson(parms));


                    Map<String, Object> parms2 = new HashMap<String, Object>(3);
                    parms2.put("type", "send");
                    parms2.put("client", client);
                    parms2.put("clients", CClient.getOnlineList());

                    log.info("JSON PARMS2: " + new Gson().toJson(parms2));

                    vertx.eventBus().send("data.on.chat", new Gson().toJson(parms2));
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

                    Map<String, Object> parms = new HashMap<String, Object>(3);

                    CClient client = CClient.unregisterClient(host, port);
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
