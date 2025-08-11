package com.example.vaadinapp.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Route("")
public class MainView extends VerticalLayout {

    private final Div sessionInfo = new Div();
    private final TextField messageField = new TextField("Session Message");
    private final Button saveButton = new Button("Save to Session");
    private final Button loadButton = new Button("Load from Session");
    private final Button refreshInfoButton = new Button("Refresh Session Info");
    private final Paragraph sessionMessage = new Paragraph();

    public MainView() {
        initializeComponents();
        setupLayout();
        updateSessionInfo();
    }

    private void initializeComponents() {
        H1 title = new H1("Vaadin 24 + Spring Boot 3.4.5 + Hazelcast Clustering");
        title.getStyle().set("color", "#2E7D32");

        messageField.setPlaceholder("Enter a message to store in session");
        messageField.setWidth("400px");

        saveButton.addClickListener(e -> saveToSession());
        loadButton.addClickListener(e -> loadFromSession());
        refreshInfoButton.addClickListener(e -> updateSessionInfo());

        saveButton.getStyle().set("background-color", "#1976D2");
        saveButton.getStyle().set("color", "white");
        
        loadButton.getStyle().set("background-color", "#388E3C");
        loadButton.getStyle().set("color", "white");
        
        refreshInfoButton.getStyle().set("background-color", "#F57C00");
        refreshInfoButton.getStyle().set("color", "white");

        sessionInfo.getStyle().set("border", "1px solid #ccc");
        sessionInfo.getStyle().set("padding", "10px");
        sessionInfo.getStyle().set("background-color", "#f9f9f9");
        sessionInfo.getStyle().set("border-radius", "5px");
        sessionInfo.setWidth("600px");

        sessionMessage.getStyle().set("font-weight", "bold");
        sessionMessage.getStyle().set("color", "#1565C0");
    }

    private void setupLayout() {
        setSpacing(true);
        setPadding(true);
        setAlignItems(Alignment.START);

        add(
            new H1("Vaadin 24 + Spring Boot 3.4.5 + Hazelcast Clustering"),
            new Paragraph("This application demonstrates session clustering using Hazelcast."),
            sessionInfo,
            messageField,
            new Div(saveButton, loadButton, refreshInfoButton),
            sessionMessage
        );
    }

    private void saveToSession() {
        jakarta.servlet.http.HttpSession session = getHttpSession();
        if (session != null && !messageField.isEmpty()) {
            session.setAttribute("userMessage", messageField.getValue());
            session.setAttribute("saveTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            Notification.show("Message saved to session!", 3000, Notification.Position.TOP_CENTER);
            updateSessionInfo();
        } else {
            Notification.show("Please enter a message first!", 3000, Notification.Position.TOP_CENTER);
        }
    }

    private void loadFromSession() {
        jakarta.servlet.http.HttpSession session = getHttpSession();
        if (session != null) {
            String message = (String) session.getAttribute("userMessage");
            String saveTime = (String) session.getAttribute("saveTime");
            
            if (message != null) {
                sessionMessage.setText("Loaded from session: " + message + 
                    (saveTime != null ? " (saved at: " + saveTime + ")" : ""));
                messageField.setValue(message);
            } else {
                sessionMessage.setText("No message found in session");
            }
        }
    }

    private void updateSessionInfo() {
        jakarta.servlet.http.HttpSession session = getHttpSession();
        if (session != null) {
            StringBuilder info = new StringBuilder();
            info.append("Session ID: ").append(session.getId()).append("<br>");
            info.append("Creation Time: ").append(new java.util.Date(session.getCreationTime())).append("<br>");
            info.append("Last Accessed: ").append(new java.util.Date(session.getLastAccessedTime())).append("<br>");
            info.append("Max Inactive Interval: ").append(session.getMaxInactiveInterval()).append(" seconds<br>");
            info.append("Is New: ").append(session.isNew()).append("<br>");
            
            // Display current server info
            String serverInfo = System.getProperty("server.port", "8080");
            info.append("Server Port: ").append(serverInfo).append("<br>");
            info.append("Current Time: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            sessionInfo.getElement().setProperty("innerHTML", info.toString());
        } else {
            sessionInfo.setText("No session available");
        }
    }

    private jakarta.servlet.http.HttpSession getHttpSession() {
        VaadinRequest request = VaadinService.getCurrentRequest();
        if (request != null && request instanceof com.vaadin.flow.server.VaadinServletRequest) {
            com.vaadin.flow.server.VaadinServletRequest servletRequest = (com.vaadin.flow.server.VaadinServletRequest) request;
            return servletRequest.getHttpServletRequest().getSession();
        }
        return null;
    }
}

