import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Calendar;

import static io.vertx.ext.web.handler.sockjs.BridgeEvent.Type.*;

public class CServer extends AbstractVerticle
{
    //private static int m_cntClients = 0;
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

    private int getFreePort()
    {
        try
        {
            ServerSocket socket = new ServerSocket(0);
            int port = socket.getLocalPort();
            socket.close();

            return port;
        }
        catch (IOException e)
        {
            log.error("Failed to get the free port: [" + e.toString() + "]");
            return -1;
        }
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

            String ip = event.socket().remoteAddress().host();
            String port = String.valueOf(event.socket().remoteAddress().port());

            String time = Calendar.getInstance().getTime().toString();

            JSONObject jmsg = new JSONObject();
            jmsg.put("type", "publish");
            jmsg.put("time", time);
            jmsg.put("addr", ip);
            jmsg.put("message", message);

            log.debug("Publish, ip: " + ip + ", port: " + port);

            vertx.eventBus().publish("chat.to.client", jmsg.toJSONString());
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
                if (event.type() == REGISTER)
                    CClient.count.incrementAndGet();
                else
                    CClient.count.decrementAndGet();

                String ip = event.socket().remoteAddress().host();
                String port = String.valueOf(event.socket().remoteAddress().port());
                String time = Calendar.getInstance().getTime().toString();

                JSONObject jmsg = new JSONObject();
                jmsg.put("type", "register");
                jmsg.put("count", CClient.count.get());
                jmsg.put("addr", ip);
                jmsg.put("port", port);
                jmsg.put("entryTime", time);

                if (event.type() == REGISTER) log.info("Register handler, ip:port [" + ip + ":" + port + "]");
                else log.info("Unregister handler, ip:port [" + ip + ":" + port + "]");

                vertx.eventBus().publish("chat.to.client", jmsg.toJSONString());
            }
        }).start();
    }

    protected boolean verifyMessage(String msg)
    {
        if (msg.length() == 0 || msg.length() > 140)
            return false;
        else
            return true;
    }
}
