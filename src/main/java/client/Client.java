package client;

import animation.MovingNodeAnimation;
import com.sun.mail.pop3.POP3Message;
import util.Utility;

import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Console;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.AbstractMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Objects;

public final class Client {
    /**
     * Scanner to read input from user.
     */
    private final Scanner scanner;
    /**
     * Reader imports bytes from socket
     */
    private BufferedReader bReader;
    /**
     * Writer writes bytes to socket
     */
    private BufferedWriter bWriter;
    /**
     * Client Socket
     */
    private Socket clientSocket;

    /**
     * Signed-in user
     */
    private String username;
    /**
     * User password
     */
    private String password;

    private int socketType;

    private final JavaMail javaMailInstance;

    /**
     * Standard Constructor.
     */
    public Client()  {
        this.scanner = new Scanner(System.in);
        this.javaMailInstance = JavaMail.getInstance();
      }


    /**
     * Initiates the first variables in the Client and stars a connection with the POP3 Server.
     * @param domainHost Host/IP of the server.
     * @param port Port
     * @throws UnknownHostException If Domain-name doesn't exists
     * @throws IOException If stream did disconnect or no connection or response from server.
     * @throws IllegalArgumentException If Port-number wasn't according to the range conventions
     */
    private void init(String domainHost, int port) throws UnknownHostException, IOException, IllegalArgumentException {
        switch (socketType){
            case 1 -> this.clientSocket = new Socket(InetAddress.getByName(domainHost), port);
            case 2 -> {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                this.clientSocket = factory.createSocket(InetAddress.getByName(domainHost), port);
                ((SSLSocket)this.clientSocket).startHandshake();
            }
        }

        this.bReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.bWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
    }

    /**
     * Reads a single line from the buffer.
     * @return The read line that has been read.
     * @throws IOException If connection disconnected, or data retrieval was not possible.
     */
    private String singleLineResponse() throws IOException {
        return bReader.readLine();
    }

    /**
     * Checks the first Token of a specific server-response
     * @param response The response from the server as a string.
     * @return true if response starts with {@code +OK}, False if response starts with {@code -ERR}
     * @throws RuntimeException if either responses was detected.
     */
    private boolean isOkToken(String response){
        String token = response.split("\\s+")[0];
        if(token.equalsIgnoreCase("+OK")) return true;
        if(token.equalsIgnoreCase("-ERR")) return false;

        throw new IllegalResponseException("Server delivered unexpected response! Closing connection...");
    }

    /**
     * Checks the status of the response of the server plus its actual sent message. This method separates the initials
     * from the actual message.
     * @return True if +ok was received, otherwise {@code false}.
     */
    private AbstractMap.SimpleEntry<Boolean, String> readSingleLineContent() {
        try {
            String response = singleLineResponse();
            String[] tokens = response.split("\\s+");
            return new AbstractMap.SimpleEntry<>(isOkToken(response), Utility.concatinateStringFromArray(1, tokens.length, tokens));
        } catch (IOException e) {
            shutdownClient();
        }
        return null;
    }

    /**
     * Login prompt that handles user input/output with the server.
     * @return {@code false} if user entered quit, otherwise true.
     */
    private int loginPrompt(){

        while (true){
            System.out.print("\rUsername: ");
            String username = scanner.nextLine();
            if(username.equalsIgnoreCase("quit")) return -1;

            System.out.print("Password: ");
            String password = getPasswordInput();
            if(password.equalsIgnoreCase("quit")) return -1;

            this.username = username; this.password = password;

            MovingNodeAnimation animationThread = new MovingNodeAnimation("Logging in... ", 100, 20);
            animationThread.start();

            switch (socketType){
                case 1, 2 -> {
                    if(!login(username, password, animationThread)) {
                        MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
                        System.out.println("\rCredentials don't match!\n Try Again");
                        continue;
                    }

                    MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
                }

                case 3, 4 -> {
                    try {
                        javaMailInstance.connect(this.username, this.password);
                    } catch (AuthenticationFailedException e) {
                        MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
                        System.out.println("\rCredentials don't match! Try Again\n");
                        continue;
                    } catch (MessagingException e){
                        MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
                        System.out.println("\rDomain or port are not correct! Try Again!\n");

                        return 1;
                    }
                }
            }


            MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
            return 0;
        }
    }

    /**
     * Send request via the socket
     * @param message Message to send.
     * @param requestTyp Type of message.
     * @throws IOException if IO error happened
     */
    private void sendRequest(String message, RequestTyp requestTyp) throws IOException {
        String line = requestTyp.toString() + " " + message;
        bWriter.write(line.trim());
        bWriter.newLine();
        bWriter.flush();
    }

    /**
     * Initializes the login process.
     * @param username Username
     * @param password Password
     * @param animationThread animation-thread to control.
     * @return True if login was a success, otherwise false.
     */
    private boolean login(String username, String password, MovingNodeAnimation animationThread){
        try {
            sendRequest(username, RequestTyp.USER);
            if(!Objects.requireNonNull(readSingleLineContent()).getKey()) return false;
            sendRequest(password, RequestTyp.PASS);
            if(!Objects.requireNonNull(readSingleLineContent()).getKey()) return false;

        } catch (IOException e) {
            MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
            System.out.println("Error while sending the username!\nTry Again");
            return false;
        }

        return true;
    }

    /**
     * Get password-input based on the type of terminal used to run this application.
     * @return The inputted password.
     */
    private String getPasswordInput(){
        Console console = System.console();
        if(console == null){
            return scanner.nextLine();
        }

        return String.valueOf(console.readPassword());
    }

    private List<String> readMultiLineResponse(){
        AbstractMap.SimpleEntry<Boolean, String> firstResponse = readSingleLineContent();

        assert firstResponse != null;
        if(!firstResponse.getKey()) return null;

        char terminatingChar = 0x2E;

        try{
            List<String> items = new ArrayList<>();
            String line;
            while (!(line = singleLineResponse()).equalsIgnoreCase(String.valueOf(terminatingChar))){
                items.add(line.trim());
            }

            return items;
        } catch (IOException e){
            System.out.println("\rError fetching data!");
        }
        return null;
    }



    /**
     * Prompt that handles user/server IO
     * @return True if connection was a success, otherwise false.
     */
    private boolean portHostPrompt(){
        System.out.println("Enter \"quit\" to close app\n");

        while (true){
            System.out.print("Server domain name aka host or IP-Address: ");
            String domain = scanner.nextLine();

            if(domain.equalsIgnoreCase("quit")) return true;

            System.out.print("Port: ");
            String port = scanner.nextLine();

            if(port.equalsIgnoreCase("quit")) return true;
            if(!Utility.isPositiveInteger(port)){
                System.out.println("Port wasn't a valid number!\nTry Again!\n\n");
                continue;
            }

            MovingNodeAnimation runningThread = new MovingNodeAnimation("Attempting to connect... ", 100, 20);

            switch (socketType){
                case 1, 2 -> {
                    runningThread.start();
                    try {
                        init(domain, Integer.parseInt(port));

                    } catch (UnknownHostException e){
                        MovingNodeAnimation.touchBarrierAndStopThread(runningThread);
                        System.out.println("\rHost/IP isn't responding!\nTry Again\n\n");
                        continue;
                    } catch (IllegalArgumentException e){
                        MovingNodeAnimation.touchBarrierAndStopThread(runningThread);
                        System.out.println("\nPort number is over 65535!\nTry Again");
                        continue;
                    } catch (IOException e){
                        MovingNodeAnimation.touchBarrierAndStopThread(runningThread);
                        System.out.println("\nLost connection!\nTry Again");
                        continue;
                    }

                    MovingNodeAnimation.touchBarrierAndStopThread(runningThread);
                    readSingleLineContent();
                }

                case 3 -> javaMailInstance.initConnectProperties(domain, port, false);
                case 4 -> javaMailInstance.initConnectProperties(domain, port, true);
            }

            return false;
        }
    }

    /**
     * Shuts down client and releases resources.
     */
    public void shutdownClient(){
        try {
            Objects.requireNonNull(bWriter).write("quit");
            bWriter.newLine();
            Objects.requireNonNull(clientSocket).close();
            Objects.requireNonNull(bReader).close();
            Objects.requireNonNull(bWriter).close();


            System.out.println("\rClient shutdown");
        } catch (IOException e) {
            System.out.println("\rLost connection...");
        } catch (NullPointerException ignored){}
    }

    private boolean retrieveMessage(MovingNodeAnimation animationThread) throws IOException {
        while(true){
            System.out.print("Message number: ");
            String num = scanner.nextLine().trim();
            if(!Utility.isPositiveInteger(num)){
                System.out.println("Not a positive integer! Try Again!\n");
                continue;
            }

            animationThread.start();

            switch (socketType){
                case 1, 2 -> {
                    sendRequest(num, RequestTyp.RETR);
                    List<String> messageLines = readMultiLineResponse();
                    if(messageLines == null){
                        MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
                        System.out.println("\rMessage number is out of bounds!");
                        return false;
                    }

                    MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
                    System.out.print("\r");
                    messageLines.forEach(System.out::println);
                }

                case 3, 4 -> {
                    try {
                        javaMailInstance.updateMessages();
                        List<Message> messages = javaMailInstance.getMessages();
                        MovingNodeAnimation.touchBarrierAndStopThread(animationThread);
                        System.out.print("\r\n");
                        System.out.println(javaMailInstance.readMessage(messages.get(Integer.parseInt(num))));
                    } catch (MessagingException | ArrayIndexOutOfBoundsException e) {
                        return false;
                    }
                }
            }


            return true;
        }
    }

    private void viewDashboard(){
        System.out.println("\r\n1- View message count and size.");
        System.out.println("2- View list of messages.");
        System.out.println("3- View specific message.");
        System.out.println("5- Close connection.");
        System.out.println("6- Show those instructions again.");

        operationPrompt();
    }

    private int[] getStat(){
        int[] arr = null;

        switch (socketType){
            case 1, 2 -> {
                try {
                    sendRequest("", RequestTyp.STAT);
                } catch (IOException e) {
                    System.out.println("Error while sending count-request to server!");
                    return null;
                }

                AbstractMap.SimpleEntry<Boolean, String> response = readSingleLineContent();
                assert response != null;
                if(!response.getKey()){
                    System.out.println("Error received from server!");
                    return null;
                }

                String[] tokens = response.getValue().split("\\s+");

                arr = new int[]{Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1])};
            }

            case 3, 4 -> {
                try {
                    javaMailInstance.updateMessages();
                    List<Message> messages = javaMailInstance.getMessages();
                    arr = new int[2];
                    arr[0] = messages.size();


                    arr[1] = javaMailInstance.getEmailInbox().getSize();
                } catch (MessagingException e) {
                    return null;
                }
            }
        }


        return arr;
    }

    private List<String> getListOfEmails(){
        List<String> list = null;

        switch (socketType){
            case 1, 2 -> {
                try {
                    sendRequest("", RequestTyp.LIST);
                } catch (IOException e) {
                    System.out.println("Error while sending count-request to server!");
                    return null;
                }
                list = readMultiLineResponse();
            }

            case 3, 4 -> {
                try {
                    javaMailInstance.updateMessages();

                    list = new ArrayList<>();
                    List<Message> messages = javaMailInstance.getMessages();
                    int[] sizes = javaMailInstance.getEmailInbox().getSizes();

                    for (int i = 0; i < messages.size(); i++)
                        list.add(messages.get(i).getMessageNumber() + " " + sizes[i]);

                } catch (MessagingException e) {
                    return null;
                }
            }
        }


        return list;
    }

    private void operationPrompt(){
        try{
            System.out.print("\r\n\nYour Choice: ");
            int choice = Integer.parseInt(scanner.nextLine().trim());

            MovingNodeAnimation loadingThread = new MovingNodeAnimation("Fetching... ", 100, 20);

            switch (choice){
                case 1 -> {
                    loadingThread.start();
                    int[] tokens = getStat();
                    MovingNodeAnimation.touchBarrierAndStopThread(loadingThread);
                    if(tokens == null){
                        operationPrompt();
                        return;
                    }

                    System.out.println("\rThere are " + tokens[0] + " message(s)." + " ".repeat(10) + "Size: " +
                            String.format("%.2fMB", ((double)tokens[1] / 1e6)));
                    System.out.println();
                    operationPrompt();
                }
                case 2 -> {
                    loadingThread.start();
                    List<String> items = getListOfEmails();
                    MovingNodeAnimation.touchBarrierAndStopThread(loadingThread);
                    System.out.print("\r");
                    if(items == null){
                        operationPrompt();
                        return;
                    }

                    System.out.println("\rMessage-number (Size in KB)\n");
                    items.forEach(message -> {
                        String[] tokens = message.split("\\s+");
                        System.out.println((Integer.parseInt(tokens[0]) - 1) +
                                "\t(" + String.format("%.2fKB", ((double)Integer.parseInt(tokens[1]) / 1e3)) + ")");
                    });
                    operationPrompt();
                }
                case 3 -> {
                    if(!retrieveMessage(loadingThread)){
                        MovingNodeAnimation.touchBarrierAndStopThread(loadingThread);
                        System.out.println("\rMessage was not found!");
                    }
                    MovingNodeAnimation.touchBarrierAndStopThread(loadingThread);
                    viewDashboard();

                }
                case 5 -> shutdownClient();
                case 6 -> {
                    System.out.println();
                    viewDashboard();
                }
                default -> {
                    System.out.println("\rNumber is out of range of choices!\nTry Again");
                    viewDashboard();
                }
            }

        } catch (NumberFormatException e){
            System.out.println("Numeric input should be entered!\nTry Again");
            viewDashboard();
        } catch (IOException e){
            System.out.println("Error fetching data from server!\n Try Again");
            viewDashboard();
        }
    }


    private boolean socketSelectorPrompt(){

        while (true) {
            System.out.println("Choose type of connection/socket:\n");
            System.out.println("1. Unencrypted Socket");
            System.out.println("2. SSL-Socket");
            System.out.println("3. JavaMail API");
            System.out.println("4. JavaMail API secured");
            System.out.println("5. Exit program");
            System.out.print("\nYour choice: ");

            String choice = scanner.nextLine().trim();
            if(!Utility.checkIntegerInBounds(1, 5, choice)){
                System.out.println("Bad Input! Try Again!\n");
                continue;
            }
            int choiceNumeric = Integer.parseInt(choice);
            if(choiceNumeric == 5) return false;

            socketType = choiceNumeric;

            return true;
        }
    }

    private boolean javaMailConnectLoginPrompt(){
        while (true){
            //false if user entered quit
            //it doesn't check connection validation
            if(portHostPrompt()) {
                System.out.println("See ya!");
                return false;
            }

            int loginPromptFlag = loginPrompt();

            switch (loginPromptFlag){
                //Quit state
                case -1 -> {
                    shutdownClient();
                    System.out.println("See ya!");
                    return false;
                }
                //Success
                //Host/port/username/password -> correct input
                case 0 -> {
                    return true;
                }
            }
        }
    }


    /**
     * Starts Client initialization
     */
    public void startClient(){
        if(!socketSelectorPrompt()){
            System.out.println("See ya!");
            return;
        }

        switch (socketType){
            case 1, 2 -> {
                if(portHostPrompt()) {
                    System.out.println("See ya!");
                    return;
                }

                System.out.println("\rConnection established!" + "\s".repeat(25) + "\n");

                if(loginPrompt() == -1){
                    shutdownClient();
                    System.out.println("See ya!");
                    return;
                }
            }

            case 3, 4 -> {
                if(!javaMailConnectLoginPrompt()){
                    shutdownClient();
                    return;
                }
            }
        }


        System.out.println("\n\nConnected as " + username + ":");
        System.out.println("********************************");
        viewDashboard();

    }
}
