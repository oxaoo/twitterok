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
        //назначение адресов для моста шины событий.
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("chat.to.server"))
                .addOutboundPermitted(new PermittedOptions().setAddress("chat.to.client"));

        mHandler.bridge(opts, event -> {
            if (event.type() == PUBLISH || event.type() == SEND) publishEvent(event);

            if (event.type() == REGISTER || event.type() == UNREGISTER) registerEvent(event);

            event.complete(true);
        });
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
            String port = String.valueOf(event.socket().remoteAddress().port());

            String time = Calendar.getInstance().getTime().toString();

            Map<String, Object> parms = new HashMap<String, Object>(4);
            parms.put("type", "publish");
            parms.put("time", time);
            parms.put("host", ip);
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
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Gson gson = new Gson();
                String host = event.socket().remoteAddress().host();
                int port = event.socket().remoteAddress().port();
                Date time = Calendar.getInstance().getTime();

                if (event.type() == REGISTER)
                    new CClient(host, port, time);
                else
                    CClient.unregisterClient(host, port);

                Map<String, Object> parms = new HashMap<String, Object>(3);
                parms.put("type", "register");
                parms.put("online", CClient.getOnline());
                //parms.put("host", host);
                //parms.put("port", port);
                //parms.put("logontime", time.toString());
                //parms.put("onlinelist", CClient.toJsonList());
                parms.put("onlinelist", CClient.getOnlineList());

                log.info("JSON = " + gson.toJson(parms));

                if (event.type() == REGISTER) log.info("Register handler, host:port [" + host + ":" + port + "]");
                else log.info("Unregister handler, host:port [" + host + ":" + port + "]");

                vertx.eventBus().publish("chat.to.client", gson.toJson(parms));
            }
        }).start();
    }

    protected boolean verifyMessage(String msg)
    {
        if (msg.length() < 1 || msg.length() >= 140)
            return false;
        else
            return true;
    }
}
