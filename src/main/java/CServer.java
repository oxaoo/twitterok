import io.vertx.core.AbstractVerticle;
import io.vertx.core.Starter;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Consumer;

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

        mHandler.bridge(opts, event ->
        {
            if (event.type() == PUBLISH || event.type() == SEND)
                publishEvent(event);

            //клиент присоединился/покинул чат.
            if (event.type() == SOCKET_CREATED || event.type() == SOCKET_CLOSED)
                socketEvent(event);

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

            // TODO время.

            //SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yy HH:mm:ss");
            //String time = sdf.format(Calendar.getInstance().getTime());
            String time = Calendar.getInstance().getTime().toString();
            //String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date.from(Instant.now()));

            JSONObject jmsg = new JSONObject();
            jmsg.put("type", "publish");
            jmsg.put("count", CClient.count.get());
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

    protected boolean socketEvent(BridgeEvent event)
    {
        //m_cntClients = (event.type() == SOCKET_CREATED) ? m_cntClients + 1 : m_cntClients - 1;

        if (event.type() == SOCKET_CREATED)
            CClient.count.incrementAndGet();
        else
            CClient.count.decrementAndGet();

        String ip = event.socket().remoteAddress().host();
        String port = String.valueOf(event.socket().remoteAddress().port());

        JSONObject jmsg = new JSONObject();
        jmsg.put("type", "socket");
        jmsg.put("count", CClient.count.get());
        jmsg.put("addr", ip);
        jmsg.put("port", port);

        if (event.type() == SOCKET_CREATED) log.info("Create socket, ip:port [" + ip + ":" + port + "]");
        else log.info("Closed socket, ip:port [" + ip + ":" + port + "]");

        vertx.eventBus().publish("chat.to.client", jmsg.toJSONString());
        return true;
    }

    protected boolean verifyMessage(String msg)
    {
        if (msg.length() == 0 || msg.length() > 140)
            return false;
        else
            return true;
    }
}
