package it.fvaleri.example.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet("/prod")
public class Producer extends HttpServlet {
    private final static Logger LOG = LoggerFactory.getLogger(Producer.class);
    private static final long serialVersionUID = 1L;

    @Resource(mappedName = "java:/jms/sourcePooledCF")
    private ConnectionFactory cf;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/html");
        PrintWriter out = res.getWriter();
        try {
            int numOfMsgs = req.getParameter("nom") != null ? Integer.parseInt(req.getParameter("nom")) : 1;
            int msgDelayMs = req.getParameter("dms") != null ? Integer.parseInt(req.getParameter("dms")) : 1;

            Connection conn = cf.createConnection();
            out.write("Client connected<br/>");

            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Destination dest = session.createQueue("SourceQueue");

            MessageProducer producer = session.createProducer(dest);
            TextMessage message = session.createTextMessage();

            for (int i = 0; i < numOfMsgs; i++) {
                message.setText("test" + i);
                producer.send(message);
                out.write(message.getText() + "</br>");
                Thread.sleep(msgDelayMs);
            }

            conn.close();
        } catch (Throwable e) {
            LOG.error("Client error", e);
            out.write("<p>Client error</p>");
        } finally {
            out.close();
        }
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        doGet(req, res);
    }
}

