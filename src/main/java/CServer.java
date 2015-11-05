import io.vertx.core.AbstractVerticle;
import io.vertx.core.Starter;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.json.simple.JSONObject;

import java.net.InetAddress;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;

import static io.vertx.ext.web.handler.sockjs.BridgeEvent.Type.*;

/**
 * Created by Alexander on 03.11.2015.
 */
public class CServer extends AbstractVerticle
{
    private static int m_cntClients = 0;
    private Logger m_log = LoggerFactory.getLogger(CServer.class);

    @Override
    public void start() throws Exception
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
                m_log.warn("Invalid port: [" + Starter.PROCESS_ARGS.get(0) + "]");
            }
        }

        m_log.info("Port: " + hostPort);

        Router router = Router.router(vertx);

        //назначение адресов для моста шины событий.
        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddress("chat.to.server"))
                .addOutboundPermitted(new PermittedOptions().setAddress("chat.to.client"));

        //мост шины событий.
        SockJSHandler ebHandler = SockJSHandler.create(vertx);


        ebHandler.bridge(opts, event -> {

            if (event.type() == PUBLISH || event.type() == SEND)
            {
                if (event.rawMessage().getString("address").equals("chat.to.server"))
                {
                    String message = event.rawMessage().getString("body");
                    String ip = event.socket().remoteAddress().host();
                    String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date.from(Instant.now()));

                    JSONObject jmsg = new JSONObject();
                    jmsg.put("type", "publish");
                    jmsg.put("count", m_cntClients);
                    jmsg.put("time", time);
                    jmsg.put("addr", ip);
                    jmsg.put("message", message);

                    String port = String.valueOf(event.socket().remoteAddress().port());
                    m_log.debug("Publish, ip: " + ip + ", port: " + port);

                    vertx.eventBus().publish("chat.to.client", jmsg.toJSONString());
                }
            }

            //клиент присоединился/покинул чат.
            if (event.type() == SOCKET_CREATED || event.type() == SOCKET_CLOSED)
            {
                m_cntClients = (event.type() == SOCKET_CREATED) ? m_cntClients + 1 : m_cntClients - 1;

                String ip = event.socket().remoteAddress().host();
                String port = String.valueOf(event.socket().remoteAddress().port());

                JSONObject jmsg = new JSONObject();
                jmsg.put("type", "socket");
                jmsg.put("count", m_cntClients);
                jmsg.put("addr", ip);
                jmsg.put("port", port);

                if (event.type() == SOCKET_CREATED)
                    m_log.info("Create socket, ip:port [" + ip + ":" + port + "]");
                else
                    m_log.info("Closed socket, ip:port [" + ip + ":" + port + "]");

                vertx.eventBus().publish("chat.to.client", jmsg.toJSONString());
            }

            event.complete(true);
        });

        router.route("/eventbus/*").handler(ebHandler);
        router.route().handler(StaticHandler.create());

        //запуск вер-сервера.
        vertx.createHttpServer().requestHandler(router::accept).listen(hostPort);

        m_log.info("Access to \"twitterok\" at the following address: \nhttp://" + InetAddress.getLocalHost().getHostAddress() + ":" + hostPort);
    }
}
