package webserver;

import java.io.*;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Collection;

import common.util.FileManager;
import db.Database;
import dto.HttpRequest;
import dto.HttpResponse;
import http.MethodMapper;
import http.ExceptionHandler;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import session.SessionManager;

import static common.WebServerConfig.LOGIN_FILE_PATH;
import static dto.HttpResponse.*;
import static http.constants.Status.*;

public class RequestHandler implements Runnable {
    public static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        logger.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            DataOutputStream dos = new DataOutputStream(out);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            HttpRequest request = new HttpRequest(br);
            HttpResponse response = new HttpResponse();

            String requestMethod = request.getMethod();
            String requestPath = request.getPath();
            try {
                // 정적 컨텐츠 처리
                if (requestMethod.equals("GET")
                        && (FileManager.getFile(requestPath, getContentType(requestPath))) != null) {
                    response.makeBody(OK, requestPath);

                    // 쿠키로부터 세션 아이디 획득
                    String sessionId = null;
                    if (request.getHeaders().get("Cookie") != null && request.getHeaders().get("Cookie").contains("SID")) {
                        sessionId = request.getCookie("SID");
                    }

                    // 사용자가 로그인 상태일 경우 /index.html에서 사용자 이름을 표시함
                    if ((response.getHeaders().get("Content-Type").equals("text/html")) && sessionId != null) {
                        String htmlPage = new String(response.getBody());
                        User user = SessionManager.getUser(sessionId);
                        response.makeDynamicHtmlBody(OK, htmlPage.replace("로그인", user.getName()));
                    }

                    // 사용자가 로그인 상태일 경우 /user/list.html에서 사용자 목록을 출력함
                    if (request.getPath().equals("/user/list.html")) {
                        if (sessionId == null) {
                            response = new HttpResponse().makeRedirect(LOGIN_FILE_PATH);
                        } else {
                            String htmlPage = new String(response.getBody());
                            StringBuilder usersTableHtml = getUserListHtml();
                            response.makeDynamicHtmlBody(OK, htmlPage.replace("<tr></tr>", usersTableHtml));
                        }
                    }
                }
                // 동적 컨텐츠 처리
                else {
                    String endPoint = requestMethod + " " + requestPath;
                    Method method = MethodMapper.getMethod(endPoint);
                    response = (HttpResponse) method.invoke(method.getDeclaringClass(), request.getBody());
                }
            } catch (Exception e) {
                ExceptionHandler.process(e, response);
            }

            ResponseHandler.send(dos, response);
        } catch (Exception e) {
            logger.debug(e.getMessage());
        }
    }

    private static StringBuilder getUserListHtml() {
        Collection<User> users = Database.findAll();
        StringBuilder usersTableHtml = new StringBuilder();
        int rowIndex = 1;
        for (User user : users) {
            usersTableHtml.append("<tr>\n");
            usersTableHtml.append(
                    String.format("<th scope=\"row\">%d</th> " +
                                    "<td>%s</td> " +
                                    "<td>%s</td> " +
                                    "<td>%s</td>" +
                                    "<td><a href=\"#\" " +
                                    "class=\"btn btn-success\" " +
                                    "role=\"button\">수정</a></td>\n",
                            rowIndex, user.getUserId(), user.getName(), user.getEmail()));
            usersTableHtml.append("</tr>\n");
            rowIndex++;
        }
        return usersTableHtml;
    }
}
