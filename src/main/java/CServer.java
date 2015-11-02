import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

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

    // Convenience method so you can run it in your IDE
    public static void main(String[] args)
    {
        VertxOptions options = new VertxOptions().setClustered(false);
        String dir = "twitterok/src/main/java/";

        if (options == null)
        {
            // Default parameter
            options = new VertxOptions();
        }

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

        Consumer<Vertx> runner = vertx -> {
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
            Vertx.clusteredVertx(options, res -> {
                if (res.succeeded())
                {
                    Vertx vertx = res.result();
                    runner.accept(vertx);
                } else
                {
                    res.cause().printStackTrace();
                }
            });
        } else
        {
            Vertx vertx = Vertx.vertx(options);
            runner.accept(vertx);
        }

    }
    /*
    {
        Runner.runExample(CServer.class);
    }*/

    @Override
    public void start() throws Exception
    {

        Router router = Router.router(vertx);

        // Allow events for the designated addresses in/out of the event bus bridge
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("chat.to.server"))
                .addOutboundPermitted(new PermittedOptions().setAddress("chat.to.client"));

        // Create the event bus bridge and add it to the router.
        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        // Create a router endpoint for the static content.
        router.route().handler(StaticHandler.create());

        // Start the web server and tell it to use the router to handle requests.
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);

        EventBus eb = vertx.eventBus();

        // Register to listen for messages coming IN to the server
        eb.consumer("chat.to.server").handler(message -> {
            // Create a timestamp string
            String timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date.from(Instant.now()));
            // Send the message back out to all clients with the timestamp prepended.
            eb.publish("chat.to.client", timestamp + ": " + message.body());
        });

    }
}
