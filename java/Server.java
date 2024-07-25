import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import io.github.cdimascio.dotenv.Dotenv;

public class Server {

    private static final int PORT = 8001;
    private static Connection connection;
    private static Dotenv dotenv = Dotenv.load();

    public static String get(String key) {
        return dotenv.get(key);
    }
    
    /**
     * The main method that starts the server and handles client connections.
     *
     * @param  args    the command-line arguments passed to the program
     * @throws ClassNotFoundException if the JDBC driver class is not found
     */
    public static void main(String[] args) throws ClassNotFoundException {
        String dbHost = Server.get("DB_HOST");
        String dbPort = Server.get("DB_PORT");
        String dbName = Server.get("DB_NAME");
        String dbUser = Server.get("DB_USER");
        String dbPass = Server.get("DB_PASS");
        System.out.println("Server is starting...");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://"+dbHost+":"+dbPort+"/"+dbName, dbUser, dbPass);
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("Server is listening on port " + PORT);
                while (true) {
                    new ClientHandler(serverSocket.accept(), connection).start();
                }
            } catch (IOException ex) {
                System.out.println("Server error: " + ex.getMessage());
                ex.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler extends Thread {
    private final Socket socket;
    private final Connection connection;
    private final String txtFilePath = "applicants.txt"; 
    private Properties emailProperties;

    private String emailHost = Server.get("EMAIL_HOST");
    private String emailUser = Server.get("EMAIL_USER");
    private String emailPass = Server.get("EMAIL_PASS");

    public ClientHandler(Socket socket, Connection connection) {
        this.socket = socket;
        this.connection = connection;
        emailProperties = new Properties();
        emailProperties.put("mail.smtp.host", emailHost);
        emailProperties.put("mail.smtp.socketFactory.port", "465");
        emailProperties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        emailProperties.put("mail.smtp.auth", "true");
        emailProperties.put("mail.smtp.port", "465");
    }

    /**
     * A method that runs the server operations, handling incoming requests.
     *
     */
    public void run() {
        try {
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            String request;

            while ((request = reader.readLine()) != null) {
                System.out.println("Received from client: " + request);
                handleRequest(request, writer,reader);
            }

            socket.close();
        } catch (IOException ex) {
            System.out.println("Server exception:" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Handles different types of requests based on the command provided.
     *
     * @param  request  the request input
     * @param  writer   the PrintWriter for writing responses
     * @param  reader   the BufferedReader for reading input
     */
    private void handleRequest(String request, PrintWriter writer,BufferedReader reader){
        String[] parts = request.split(" ");
        String command = parts[0];

        switch (command) {
            case "register":
                registerApplicant(parts, writer);
                break;
            case "registerSchool":
                registerSchool(parts, writer);
                break;
            case "viewChallenges":
                viewChallenges(writer);
                break;
            case "confirm":
                confirmApplicant(parts, writer);
                break;
            case "attemptChallenge":
                attemptChallenge(new Scanner(reader), writer, String.valueOf(parts[1]), Integer.parseInt(parts[2]));
                break;
            case "viewApplicants":
                viewApplicants(writer);
                break;
            case "login":
                if ("school_representative".equals(parts[1])) {
                    loginSchoolRepresentative(new Scanner(reader), writer);
                } else if ("participant".equals(parts[1])) {
                    loginParticipant(new Scanner(reader), writer);
                }
                break;
            default:
                writer.println("Invalid command");
                break;
        }
    }

    /**
     * Registers an applicant in the system.
     *
     * @param  parts   an array of strings containing the applicant's information
     * @param  writer  a PrintWriter object for writing the response
     * @return          void
     */
    private void registerApplicant(String[] parts, PrintWriter writer) {
        String username = parts[1];
        String firstName = parts[2];
        String lastName = parts[3];
        String schoolRegNumber = parts[4];
        String email = parts[5];
        String dob = parts[6];
        String password = parts[7];
        String imagePath = parts[8];

        try {
            // Check if the applicant is in the rejected_applicants table
            String checkQuery = "SELECT * FROM rejected_applicants WHERE username = ?";
            PreparedStatement checkStatement = connection.prepareStatement(checkQuery);
            checkStatement.setString(1, username);
            ResultSet checkResult = checkStatement.executeQuery();

            if (checkResult.next()) {
                writer.println("Registration failed. This applicant has been rejected previously.");
                return; 
            }
            // Read the image file
            File imageFile = new File(imagePath);
            FileInputStream fis = new FileInputStream(imageFile);
            byte[] imageBytes = new byte[(int) imageFile.length()];
            fis.read(imageBytes);
            fis.close();    

            String query = "INSERT INTO applicants (username, firstname, lastname, school_registration_number, email, date_of_birth,password,image) VALUES (?, ?, ?, ?, ?, ?,?,?)";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, username);
            statement.setString(2, firstName);
            statement.setString(3, lastName);
            statement.setString(4, schoolRegNumber);
            statement.setString(5, email);
            statement.setDate(6, Date.valueOf(dob));
            statement.setString(7, password);
            statement.setBytes(8, imageBytes);

            int rowsInserted = statement.executeUpdate();
            if (rowsInserted > 0) {
                writer.println("Applicant registered successfully!");
                // Get the representative email for the school
                String representativeEmail = getRepresentativeEmailBySchoolRegNumber(schoolRegNumber);

                logToTextFile(String.join(" ", username,firstName,lastName,schoolRegNumber,email,dob));
                if (representativeEmail != null && !representativeEmail.equals("not found") && !representativeEmail.isEmpty()) {
                    // Send email notification to representative
                    sendEmailNotification(representativeEmail, "Confirmation", "Please confirm the applicant: " + username);
                } else {
                    writer.println("Error: Representative email not found for school registration number: " + schoolRegNumber);
                }
            }

        } catch (SQLException | IOException | MessagingException e) {
            e.printStackTrace();
            writer.println("Error registering applicant: " + e.getMessage());
        }
    }

    /**
     * Retrieves the representative email associated with a school registration number.
     *
     * @param  schoolRegNumber   the registration number of the school
     * @return                   the representative email associated with the school
     * @throws SQLException     if an error occurs while executing the SQL query
     */
    private String getRepresentativeEmailBySchoolRegNumber(String schoolRegNumber) throws SQLException {
        String query = "SELECT representative_email FROM schools WHERE school_registration_number = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, schoolRegNumber);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
            return rs.getString("representative_email");
        } else {
            throw new SQLException("No representative email found for school registration number: " + schoolRegNumber);
        }
    }
    
    /**
     * Retrieves the details of all applicants from the database and prints them to the provided PrintWriter.
     *
     * @param  writer    the PrintWriter object to write the applicant details to
     * @throws SQLException if an error occurs while executing the SQL query
     */
    private void viewApplicants(PrintWriter writer) {
        try {
            String query = "SELECT username, school_registration_number FROM applicants";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
    
            // display applicants
            writer.println("\n:: Applicant Details ::");
            while (resultSet.next()) {
                String username = resultSet.getString("username");
                String schoolRegNumber = resultSet.getString("school_registration_number");
    
                writer.println("Username: " + username);
                writer.println("School Registration Number: " + schoolRegNumber);
                writer.println();
            }
            writer.println("END_OF_RESPONSE"); // End of response signal
            writer.flush();
        } catch (SQLException e) {
            e.printStackTrace();
            writer.println("Error viewing applicants: " + e.getMessage());
            writer.flush();
        }
    }
    
    /**
     * Retrieves a shuffled list of question IDs associated with the given challenge ID from the database.
     *
     * @param  challengeId  the ID of the challenge
     * @return              a list of shuffled question IDs
     */
    private List<Integer> shuffleQuestions(int challengeId){
        List<Integer> questionIds = new ArrayList<>();
        try {
            String query = "SELECT question_id FROM challenge_questions WHERE challenge_id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, challengeId);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                int questionId = resultSet.getInt("question_id");
                questionIds.add(questionId);
            }

            Collections.shuffle(questionIds);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return questionIds;
    }
    
    /**
     * A method to log in a participant using the provided Scanner and PrintWriter objects.
     *
     * @param  scanner  the Scanner object used to read input
     * @param  writer   the PrintWriter object used to write output
     * @return          true if the login is successful, false otherwise
     */
    private boolean loginParticipant(Scanner scanner,PrintWriter writer) {
        try {
            String username = scanner.nextLine();
            String password = scanner.nextLine().trim();

            String query = "SELECT * FROM participants WHERE username = ? AND password = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, username);
            statement.setString(2, password);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                writer.println("Login successful!");
                writer.flush();
                return true;
            } else {
                writer.println("Invalid username or password");
                writer.flush();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            writer.println("Error during login: " + e.getMessage());
            writer.flush();
            return false;
        }
    }

    /**
     * Logs in a school representative using the provided Scanner and PrintWriter objects.
     *
     * @param  scanner  the Scanner object used to read input
     * @param  writer   the PrintWriter object used to write output
     * @return          true if the login is successful, false otherwise
     */
    private boolean loginSchoolRepresentative(Scanner scanner, PrintWriter writer) {
        String username = scanner.nextLine();
        String password = scanner.nextLine().trim();

        try {
            String query = "SELECT * FROM school_representatives WHERE username = ? AND password = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, username);
            statement.setString(2, password);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                writer.println("Login successful!");
                writer.flush();
                return true;
            } else {
                writer.println("Invalid username or password");
                writer.flush();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            writer.println("Error during login: " + e.getMessage());
            writer.flush();
            return false;
        }
    }
  
    /**
     * Method to view challenges and display challenge details.
     *
     * @param  writer   PrintWriter to write challenge details
     */
    private void viewChallenges(PrintWriter writer) {
        try {
            String query = "SELECT c.id, c.name, c.start_date, c.end_date, c.duration, c.description, COUNT(q.id) AS num_questions " +
                       "FROM challenges c " +
                       "LEFT JOIN challenge_questions cq ON c.id = cq.challenge_id " +
                       "LEFT JOIN questions q ON cq.question_id = q.id " +
                       "WHERE c.end_date >= NOW() " +
                       "GROUP BY c.id";
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
    
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                Date startDate = resultSet.getDate("start_date");
                Date endDate = resultSet.getDate("end_date");
                int duration = resultSet.getInt("duration");
                String description = resultSet.getString("description");
                int numQuestions = resultSet.getInt("num_questions");
                
                writer.println("Challenge ID: " + id);
                writer.println("Name: " + name);
                writer.println("Description: " + description);
                writer.println("Start Date: " + startDate);
                writer.println("End Date: " + endDate);
                writer.println("Duration: " + duration + " minutes");
                writer.println("Number of Questions: " + numQuestions);
                writer.println();
            }    
            // Indicate the end of challenges listing
            writer.println("END_OF_CHALLENGES");
            writer.flush();    
        } catch (SQLException e) {
            e.printStackTrace();
            writer.println("Error viewing challenges: " + e.getMessage());
            writer.flush();
        }
    }
    
    /**
     * Confirm or reject an applicant and sending an email immediately.
     *
     * @param  parts   array containing username, and reason
     * @param  writer  PrintWriter object to write confirmation/rejection messages
     */
    private void confirmApplicant(String[] parts, PrintWriter writer) {
        String confirm = parts[1];
        String username = parts[2];
        String reason = String.join(" ", Arrays.copyOfRange(parts, 3, parts.length));
    
        try {
            if (confirm.equalsIgnoreCase("yes")) {
                logToTextFile(String.join(" ", username));    
                // Move applicant to participants table
                String moveToParticipantsQuery = "INSERT INTO participants (username,firstname,lastname,school_registration_number,email,date_of_birth,password,image) SELECT username, firstname, lastname, school_registration_number, email, date_of_birth,password,image FROM applicants WHERE username = ?";
                PreparedStatement moveToParticipantsStmt = connection.prepareStatement(moveToParticipantsQuery);
                moveToParticipantsStmt.setString(1, username);
    
                int rowsInserted = moveToParticipantsStmt.executeUpdate();
                if (rowsInserted > 0) {
                    writer.println("Participant confirmed successfully!");    
                    removeFromApplicantsTable(username);    
                    removeFromFile(username);
                    sendEmailNotification(getEmailForParticipant(username), "Confirmation", "You have been confirmed as a participant.");
                    // Send email notification to school representative
                    sendEmailNotification(getEmailForRep(username), "Confirmation", "You have confirmed the applicant: " + username);
                } else {
                    writer.println("Error: No matching applicant found to confirm.");
                }    
            } else if (confirm.equalsIgnoreCase("no")) {
                logToTextFile("confirm no " + username + " " + reason);
                sendEmailNotification(getEmailForApplicant(username), "Rejection", "Your application has been rejected. Reason: " + reason); 
                // Move applicant to rejected_applicants table
                String moveToRejectedQuery = "INSERT INTO rejected_applicants (username, reason) SELECT username, ? FROM applicants WHERE username = ?";
                PreparedStatement moveToRejectedStmt = connection.prepareStatement(moveToRejectedQuery);
                moveToRejectedStmt.setString(1, reason);
                moveToRejectedStmt.setString(2, username);
                
                int rowsInserted = moveToRejectedStmt.executeUpdate();
                if (rowsInserted > 0) {
                    writer.println("Participant rejected successfully with reason: " + reason);   
                    // Remove from applicants table
                    removeFromApplicantsTable(username);    
                    // Remove from file
                    removeFromFile(username);                    
                } else {
                    writer.println("Error: No matching applicant found to reject.");
                }    
            } else {
                writer.println("Invalid confirmation command.");
            }    
        } catch (SQLException | IOException | MessagingException e) {
            e.printStackTrace();
            writer.println("Error confirming participant: " + e.getMessage());
        }
    }
    
    /**
     * Retrieves the email associated with the representative username from the school_representatives table.
     *
     * @param  username   the username of the representative
     * @return            the email address associated with the representative
     * @throws SQLException if an error occurs while executing the SQL query
     */
    private String getEmailForRep(String username) throws SQLException{
        String query = "SELECT email FROM school_representatives WHERE username = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("email");
        }
        return "not found";
    }

    /**
     * Retrieves the email associated with the participant username from the participants table.
     *
     * @param  username   the username of the participant
     * @return            the email address associated with the participant, or "not found" if not found
     */
    private String getEmailForParticipant(String username) throws SQLException{
        String query = "SELECT email FROM participants WHERE username = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("email");
        }
        return "not found";
    }

    private String getEmailForApplicant(String username) throws SQLException{
        String query = "SELECT email FROM applicants WHERE username = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getString("email");
        }
        return "not found";
    }

    /**
     * Sends an email notification to a recipient with the provided details.
     *
     * @param  recipientEmail   the email address of the recipient
     * @param  subject          the subject of the email
     * @param  messageBody      the body of the email message
     */
    private void sendEmailNotification(String recipientEmail, String subject, String messageBody) throws MessagingException {
        Session session = Session.getInstance(emailProperties,
            new javax.mail.Authenticator() {
                protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new javax.mail.PasswordAuthentication(emailUser, emailPass);
                }
            });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(emailUser));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
        message.setSubject(subject);
        message.setText(messageBody);

        Transport.send(message);
        System.out.println("Email notification sent successfully to " + recipientEmail);
    }

        /**
     * Removes an applicant from the applicants table in the database.
     *
     * @param  username   the username of the applicant to be removed
     * @throws SQLException  if a database access error occurs or other errors
     */
    private void removeFromApplicantsTable(String username) throws SQLException {
        String deleteFromApplicantsQuery = "DELETE FROM applicants WHERE username = ?";
        PreparedStatement deleteFromApplicantsStmt = connection.prepareStatement(deleteFromApplicantsQuery);
        deleteFromApplicantsStmt.setString(1, username);
        deleteFromApplicantsStmt.executeUpdate();
    }

    /**
     * Removes the specified username from the 'applicants.txt' file.
     *
     * @param  username   the username to be removed
     * @throws IOException  if an I/O error occurs
     */
    private void removeFromFile(String username) throws IOException {
        Path path = Paths.get("applicants.txt");
        List<String> lines = Files.readAllLines(path);
        lines.removeIf(line -> line.startsWith(username + " "));
        Files.write(path, lines);
    }
    
    /**
     * Attempts a challenge for a participant.
     *
     * @param scanner         the scanner to read user input
     * @param writer          the writer to write output to the user
     * @param username        the username of the participant
     * @param challengeId     the ID of the challenge
     * @throws SQLException   if there is an error with the database
     * @throws IOException     if there is an error with the file system
     * @throws DocumentException if there is an error with the PDF report
     * @throws MessagingException if there is an error with the email
     */
    private void attemptChallenge(Scanner scanner, PrintWriter writer, String username, int challengeId) {
        try {
            // Fetch participant ID from database based on username
            int participantId = getParticipantIdByUsername(username);         
            // Ensure participant ID is valid
            if (participantId == -1) {
                writer.println("Invalid participant username.");
                return;
            }        
            // Fetch challenge duration from the database
            int challengeDuration = getChallengeDuration(challengeId);
        
            // Fetch all question IDs for the challenge
            List<Integer> questionIds = shuffleQuestions(challengeId);
            int totalQuestions = questionIds.size();
            int remainingQuestions = totalQuestions;
        
            // Count existing attempts for this participant and challenge
            int attemptsCount = countAttempts(participantId, challengeId); 
        
            // Check if participant has exceeded maximum attempts
            if (attemptsCount >= 3) {
                writer.println("Max Attempts Reached!");
                writer.flush();
                return;
            }
        
            // Timer variables
            long startTime = System.currentTimeMillis();
            long endTime = startTime + (challengeDuration * 60 * 1000); //milliseconds
        
            int attemptNumber = attemptsCount + 1;
            // Array to store per question data
            List<String> reportLines = new ArrayList<>();
            int totalScore = 0;
        
            for (int i = 0; i < totalQuestions; i++) {
                int questionId = questionIds.get(i);        
                // Fetch question details using questionId
                String query = "SELECT question_text, answer, marks FROM questions WHERE id = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setInt(1, questionId);
                ResultSet questionResultSet = statement.executeQuery();
        
                if (questionResultSet.next()) {
                    String questionText = questionResultSet.getString("question_text");
                    String correctAnswer = questionResultSet.getString("answer");
                    int marks = questionResultSet.getInt("marks");
        
                    // Display remaining questions and time
                    writer.println("Remaining Questions: " + remainingQuestions);
                    displayRemainingTime(startTime, endTime, writer);
        
                    // Present question to participant
                    writer.println("Question ID: " + questionId);
                    writer.println("Question: " + questionText);
                    writer.print("Your answer: ");
                    writer.flush();
        
                    String userAnswer = scanner.nextLine().trim();
        
                    // Check answer correctness and record attempt
                    boolean isCorrect = correctAnswer.equalsIgnoreCase(userAnswer);
                    recordAttempt(participantId, challengeId, questionId, attemptNumber, isCorrect, marks, System.currentTimeMillis() - startTime);
                    // Format feedback
                    StringBuilder feedback = new StringBuilder();
                    feedback.append(userAnswer);
                    feedback.append(", ").append(isCorrect ? "Correct!" : "Incorrect! Correct answer was: " + correctAnswer);

                    // Print feedback to writer
                    writer.println(feedback.toString());
                    writer.println();

                    long timeTakenSeconds = (System.currentTimeMillis() - startTime) / 1000;
                    totalScore += (isCorrect ? marks : 0);
                    // Store question data
                    String reportLine = "Question ID: " + questionId + "\n" +
                                    "Question: " + questionText + "\n" +
                                    "Your Answer: " + userAnswer + "\n" +
                                    "Correct Answer: " + correctAnswer + "\n" +
                                    "Correct: " + isCorrect + "\n" +
                                    "Score: " + (isCorrect ? marks : 0) + "\n" +
                                    "Time Taken: " + timeTakenSeconds + " seconds" +"\n" +
                                    "Total Score: " + totalScore + "\n" +
                                    "\n";
                    reportLines.add(reportLine);
                }
        
                remainingQuestions--;
        
                // Check if time is up
                if (System.currentTimeMillis() >= endTime) {
                    writer.println("Time's up! Challenge will be closed.");
                    break;
                }
            }
        
            // Provide challenge summary after all questions are attempted
            generatePdfReport(username, challengeId, reportLines);
            writer.println("Challenge completed. Summary has been sent to your email: "+ getEmailForParticipant(username));
            sendEmailWithAttachment(getEmailForParticipant(username), "Challenge Report", "Here is your challenge report.", "reports/" + username + "_challenge_" + challengeId + ".pdf");
            writer.flush();
        
        } catch (SQLException | IOException | DocumentException | MessagingException e) {
            e.printStackTrace();
            writer.println("Error during challenge attempt: " + e.getMessage());
        }
    }

    /**
     * Generates a PDF report for a given user and challenge.
     *
     * @param  username    the username of the user
     * @param  challengeId the ID of the challenge
     * @param  reportLines  the lines of the report
     * @throws FileNotFoundException if the file cannot be found
     * @throws DocumentException     if there is an error with the PDF document
     */
    private void generatePdfReport(String username, int challengeId, List<String> reportLines) throws FileNotFoundException, DocumentException {
        String filePath = "reports/" + username + "_challenge_" + challengeId + ".pdf";
        File file = new File(filePath);
        file.getParentFile().mkdirs(); // Create parent directories if needed

        // Create the PDF document
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath));

        PdfWriter.getInstance(document, new FileOutputStream(filePath));
        document.open();

        document.add(new Paragraph("Challenge Report for " + username));
        document.add(new Paragraph("Challenge ID: " + challengeId));
        document.add(new Paragraph(" "));

        for (String line : reportLines) {
            document.add(new Paragraph(line));
        }

        document.close();
    }

    /**
     * Retrieves the participant ID from the database based on the provided username.
     *
     * @param  username  the username of the participant
     * @return           the participant ID if found, -1 otherwise
     * @throws SQLException if there is an error with the database
     */
    private int getParticipantIdByUsername(String username) throws SQLException {
        String query = "SELECT id FROM participants WHERE username = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next()) {
            return resultSet.getInt("id");
        }
        return -1; // Participant not found
    }
    
    /**
     * Counts the number of attempts made by a participant for a specific challenge.
     *
     * @param  participantId  the ID of the participant
     * @param  challengeId    the ID of the challenge
     * @return                the number of attempts made by the participant for the challenge
     */
    private int countAttempts(int participantId, int challengeId) {
        int attemptCount = 0;
        try {
            String query = "SELECT COUNT(*) AS attempt_count FROM participant_attempts " +
                           "WHERE participant_id = ? AND challenge_id = ?";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setInt(1, participantId);
            statement.setInt(2, challengeId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                attemptCount = resultSet.getInt("attempt_count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return attemptCount;
    }
    
    /**
     * Displays the remaining time in seconds based on the start and end time.
     *
     * @param  startTime    the start time in milliseconds
     * @param  endTime      the end time in milliseconds
     * @param  writer       the PrintWriter to write the remaining time
     */
    private void displayRemainingTime(long startTime, long endTime, PrintWriter writer) {
        long currentTime = System.currentTimeMillis();
        long remainingTimeMillis = endTime - currentTime;
    
        if (remainingTimeMillis <= 0) {
            writer.println("Time Remaining: 0 seconds");
        } else {
            long remainingSeconds = remainingTimeMillis / 1000;
            writer.println("Time Remaining: " + remainingSeconds + " seconds");
        }
    }

    /**
     * Retrieves the duration of a challenge from the database based on its ID.
     *
     * @param  challengeId  the ID of the challenge
     * @return              the duration of the challenge in minutes, or 0 if not found
     * @throws SQLException if there is an error executing the SQL query
     */
    private int getChallengeDuration(int challengeId) throws SQLException {
        int duration = 0;
        String query = "SELECT duration FROM challenges WHERE id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, challengeId);
            ResultSet resultSet = statement.executeQuery();
            
            if (resultSet.next()) {
                duration = resultSet.getInt("duration");
            }
        }
        
        return duration;
    }

    /**
     * Inserts a new attempt record into the participant_attempts table.
     *
     * @param  participantId   the ID of the participant
     * @param  challengeId     the ID of the challenge
     * @param  questionId      the ID of the question
     * @param  attemptNumber   the attempt number
     * @param  isCorrect       whether the attempt was correct or not
     * @param  score           the score achieved in the attempt
     * @param  timeTaken       the time taken for the attempt in milliseconds
     */
    private void recordAttempt(int participantId, int challengeId, int questionId, int attemptNumber, boolean isCorrect, int score, long timeTaken) {
        String query = "INSERT INTO participant_attempts (participant_id, challenge_id, question_id, attempt_number, is_correct, score, time_taken) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, participantId);
            statement.setInt(2, challengeId);
            statement.setInt(3, questionId);
            statement.setInt(4, attemptNumber);
            statement.setBoolean(5, isCorrect);
            statement.setInt(6, score);
            statement.setLong(7, timeTaken);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Logs the app data to a text file.
     *
     * @param  data   the data to be logged
     * @throws IOException  if an I/O error occurs while writing to the file
     */
    private void logToTextFile(String data) throws IOException {
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(txtFilePath,true))){
            writer.write(data + System.lineSeparator());
        }
    }

    /**
     * Registers a school in the system.
     *
     * @param  parts   an array of strings containing the school's information
     * @param  writer  a PrintWriter object for writing the response
     */
    private void registerSchool(String parts[],PrintWriter writer) {
        String name = parts[1];
        String district = parts[2];
        String schoolRegNumber = parts[3];
        String representativeEmail = parts[4];
        String representativeName = parts[5];

        try {
            logToTextFile(String.join(" ", parts));

            // Check if the school is in the schools table
            String checkQuery = "SELECT * FROM schools WHERE school_registration_number = ?";
            PreparedStatement checkStatement = connection.prepareStatement(checkQuery);
            checkStatement.setString(1, schoolRegNumber);
            ResultSet checkResult = checkStatement.executeQuery();
            if (checkResult.next()) {
                writer.println("School already exists");
                return;
            }

            // Add the school to the schools table
            String query = "INSERT INTO schools (name,district,school_registration_number,representative_email,representative_name) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setString(1, name);
            statement.setString(2, district);
            statement.setString(3, schoolRegNumber);
            statement.setString(4, representativeEmail);
            statement.setString(5, representativeName);

            int rowsInserted = statement.executeUpdate();
            if (rowsInserted > 0) {
                writer.println("School registered successfully!");
            }

        } catch (SQLException | IOException e) {
            e.printStackTrace();
            writer.println("Error registering school: " + e.getMessage());
        }
    }

    /**
     * Sends an email with an attachment(pdf) to the specified recipient.
     *
     * @param  to                  the email address of the recipient
     * @param  subject             the subject of the email
     * @param  body                the body of the email message
     * @param  filePath            the path to the file to be attached
     * @throws MessagingException   if there is an error sending the email
     * @throws IOException          if there is an error attaching the file
     */
    private void sendEmailWithAttachment(String to, String subject, String body, String filePath) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.zoho.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
    
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                return new javax.mail.PasswordAuthentication(emailUser, emailPass);
            }
        });
    
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(emailUser));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
    
        MimeBodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText(body);
    
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);
    
        MimeBodyPart attachmentBodyPart = new MimeBodyPart();
        try {
            attachmentBodyPart.attachFile(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        multipart.addBodyPart(attachmentBodyPart);
    
        message.setContent(multipart);
    
        Transport.send(message);
    }   
}
