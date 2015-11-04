import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeEvent;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

/**
 * Created by Alexander on 03.11.2015.
 */
public class CServer extends AbstractVerticle
{
    int m_clients = 0;

    public static void main(String[] args)
    {
        VertxOptions options = new VertxOptions().setClustered(false);
        String dir = "twitterok/src/main/java/";

        /*
        if (options == null)
        {
            // Default parameter
            options = new VertxOptions();
        }*/

        try
        {
            File current = new File(".").getCanonicalFile();
            if (dir.startsWith(current.getName()) && !dir.equals(current.getName()))
            {
                dir = dir.substring(current.getName().length() + 1);
            }
        }
        catch (IOException e)
        {}

        System.setProperty("vertx.cwd", dir);
        String verticleID = CServer.class.getName();

        Consumer<Vertx> runner = vertx ->
        {
            try
            {
                vertx.deployVerticle(verticleID);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        };

        if (options.isClustered())
        {
            Vertx.clusteredVertx(options, res ->
            {
                if (res.succeeded())
                {
                    Vertx vertx = res.result();
                    runner.accept(vertx);
                } else
                {
                    res.cause().printStackTrace();
                }
            });
        }
        else
        {
            Vertx vertx = Vertx.vertx(options);
            runner.accept(vertx);
        }

    }

    @Override
    public void start() throws Exception
    {
        Router router = Router.router(vertx);

        //назначение адресов для моста шины событий.
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("chat.to.server"))
                .addOutboundPermitted(new PermittedOptions().setAddress("chat.to.client"));

        //мост шины событий.
        SockJSHandler ebHandler = SockJSHandler.create(vertx);


        ebHandler.bridge(opts, event ->
        {
            if (event.type() == BridgeEvent.Type.PUBLISH || event.type() == BridgeEvent.Type.SEND)
            {
                //System.out.println("Type: " + event.type().toString());
                if (event.rawMessage().getString("address").equals("chat.to.server"))
                {
                    String message = event.rawMessage().getString("body");
                    String ip = event.socket().remoteAddress().host();
                    String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date.from(Instant.now()));

                    JSONObject jmsg = new JSONObject();
                    jmsg.put("type", "publish");
                    jmsg.put("time", time);
                    jmsg.put("addr", ip);
                    jmsg.put("message", message);

                    //eb.publish("chat.to.client", jmsg.toJSONString());
                    vertx.eventBus().publish("chat.to.client", jmsg.toJSONString());
                }
            }

            //new user.
            if (event.type() == BridgeEvent.Type.SOCKET_CREATED)
            {
                m_clients++;
                String ip = event.socket().remoteAddress().host();
                String port = String.valueOf(event.socket().remoteAddress().port());
                //System.out.println("New client: " + ip + ":" + port);

                JSONObject jmsg = new JSONObject();
                jmsg.put("type", "create");
                jmsg.put("count", m_clients);
                jmsg.put("addr", ip);
                jmsg.put("port", port);

                vertx.eventBus().publish("chat.to.client", jmsg.toJSONString());
            }

            //left user.
            if (event.type() == BridgeEvent.Type.SOCKET_CLOSED)
            {
                m_clients--;
                String ip = event.socket().remoteAddress().host();
                String port = String.valueOf(event.socket().remoteAddress().port());
                //System.out.println("Exit client: " + ip + ":" + port);

                JSONObject jmsg = new JSONObject();
                jmsg.put("type", "closed");
                jmsg.put("count", m_clients);
                jmsg.put("addr", ip);
                jmsg.put("port", port);

                vertx.eventBus().publish("chat.to.client", jmsg.toJSONString());
            }

            event.complete(true);
        });

        router.route("/eventbus/*").handler(ebHandler);
        router.route().handler(StaticHandler.create());

        //запуск вер-сервера.
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }
}
