import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Created by Alexander on 15.11.2015.
 */
public class CVerticleLoader
{
    public static void main(String[] args)
    {
        VertxOptions options = new VertxOptions().setClustered(false);
        String dir = "twitterok/src/main/java/";

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
}
