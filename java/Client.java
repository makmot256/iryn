import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 8001;

    /**
     * A method to handle the main logic of the client program.
     *
     * @param  args    the command-line arguments passed to the program
     * @return         void
     */
    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PORT)) {

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            Scanner scanner = new Scanner(System.in);
            handleMainMenuOptions(scanner, writer, reader);

        } catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("I/O error: " + ex.getMessage());
        }
    }

    /**
     * A method to display the main menu options.
     * 
     * @return          void
     */
    private static void displayMainMenu() {
        System.out.println("Main Menu:");
        System.out.println("1. Register");
        System.out.println("2. Login (Participant)");
        System.out.println("3. Login (School Representative)");
        System.out.println("4. Exit");
    }

    /**
     * Displays the participant menu options.
     *
     * @return          void
     */
    private static void displayParticipantMenu() {
        System.out.println("Participant Menu:");
        System.out.println("1. View Challenges");
        System.out.println("2. Attempt Challenge");
        System.out.println("3. Logout");
    }

    /**
     * A method to display the school representative menu options.
     *
     * @return          void
     */
    private static void displaySchoolRepMenu() {
        System.out.println("School Representative Menu:");
        System.out.println("1. View Applicants");
        System.out.println("2. Confirm Applicant");
        System.out.println("3. Register School");
        System.out.println("4. Logout");
    }

    /**
     * Registers an applicant by prompting the user for their information and sending it to the server.
     *
     * @param  scanner         the scanner object for user input
     * @param  writer          the print writer object for sending messages to the server
     * @param  reader          the buffered reader object for receiving messages from the server
     * @return                 void
     */
    private static void registerApplicant(Scanner scanner, PrintWriter writer,BufferedReader reader) {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("First Name: ");
        String firstName = scanner.nextLine();
        System.out.print("Last Name: ");
        String lastName = scanner.nextLine();
        System.out.print("School Registration Number: ");
        String schoolRegNumber = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Date of Birth (YYYY-MM-DD): ");
        String dob = scanner.nextLine();
        String password = readPasswordSecurely();
        System.out.print("Add image path: ");
        String imagePath = scanner.nextLine();

        writer.println("register " + username + " " + firstName + " " + lastName + " " + schoolRegNumber + " " + email + " " + dob + " " + password+ " " + imagePath);  

        try {
            String response;
            while ((response = reader.readLine()) != null && !response.isEmpty()) {
                System.out.println(response);
                if (response.contains("Applicant registered successfully!")) {
                    // displayMainMenu();
                    handleMainMenuOptions(scanner, writer, reader);
                    return;
                } else if(response.contains("Error registering applicant:")) {
                    // displayMainMenu();
                    handleMainMenuOptions(scanner, writer, reader);
                    return;
                }
            }
        } catch (IOException e) {
            System.out.println("Error during registration: " + e.getMessage());
        }
    }
    
    /**
     * Logs in a participant by prompting the user for their username and password,
     * sending the login command to the server, and handling the response.
     *
     * @param  scanner   the scanner object for user input
     * @param  writer    the print writer object for sending messages to the server
     * @param  reader    the buffered reader object for receiving messages from the server
     * @return           void
     */
    private static void loginParticipant(Scanner scanner, PrintWriter writer, BufferedReader reader) {
        try {
            System.out.print("Enter username: ");
            String username = scanner.nextLine();
            String password = readPasswordSecurely();
    
            // Send login command to server
            writer.println("login participant");
            writer.flush();
            writer.println(username); 
            writer.flush();
            writer.println(password);
            writer.flush();

            // Read response from server
            String response = reader.readLine();
            System.out.println(response);
    
            if (response.equals("Login successful!")) {
                // Display participant menu
                displayParticipantMenu();
                handleParticipantOptions(scanner, writer, reader);
            } else {
                handleMainMenuOptions(scanner, writer, reader);
            }
        } catch (IOException e) {
            System.out.println("Error during login: " + e.getMessage());
        }
    }

    /**
     * A method to securely read a password from the console.
     *
     * @return         	the password entered by the user
     */
    private static String readPasswordSecurely() {
        Console console = System.console();
        if (console == null) {
            throw new RuntimeException("No console available");
        }
        char[] passwordArray = console.readPassword("Enter password: ");
        return new String(passwordArray);
    }

    /**
     * Handles the participant options based on the user's input. Displays the participant menu and allows the user to choose
     * between viewing challenges, attempting challenges, or going back to the main menu. Reads and displays server responses
     * for options other than going back to the main menu.
     *
     * @param  scanner   the scanner object for reading user input
     * @param  writer    the print writer object for sending commands to the server
     * @param  reader    the buffered reader object for reading server responses
     */
    private static void handleParticipantOptions(Scanner scanner, PrintWriter writer, BufferedReader reader) {
        String text;
        do {
            System.out.print("Choose an option: ");
            text = scanner.nextLine();

            switch (text) {
                case "1":
                    viewChallenges(scanner,writer, reader);
                    break;
                case "2":
                    attemptChallenge(scanner, writer, reader);
                    break;
                case "3":
                    try {
                        handleMainMenuOptions(scanner, writer, reader);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    System.out.println("Invalid option");
                    break;
            }
            // Read and display server responses
            if (!text.equals("3")) {
                String response;
                try {
                    while ((response = reader.readLine()) != null && !response.isEmpty()) {
                        System.out.println(response);
                        if (response.equals("Invalid command")) {
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Error reading response: " + e.getMessage());
                }
            }
        } while (!text.equals("3")); // Exit when user chooses to go back to the main menu
    }

    private static void loginSchoolRepresentative(Scanner scanner, PrintWriter writer, BufferedReader reader) {
        try {
            System.out.print("Enter username: ");
            String username = scanner.nextLine();
            String password = readPasswordSecurely();    
            // Send login command to server
            writer.println("login school_representative");
            writer.flush();
            writer.println(username); 
            writer.flush();
            writer.println(password);
            writer.flush();
            // Read response from server
            String response = reader.readLine();
            System.out.println(response);
    
            if (response.equals("Login successful!")) {
                displaySchoolRepMenu();
                handleSchoolRepOptions(scanner, writer, reader);
            } else {
                handleMainMenuOptions(scanner, writer, reader);
            }
        } catch (IOException e) {
            System.out.println("Error during login: " + e.getMessage());
        }
    }

    /**
     * Handles the options for a school representative. Allows them to view applicants, confirm applicants, register a school, or go back to the main menu.
     *
     * @param  scanner   the scanner object for reading user input
     * @param  writer    the print writer object for sending commands to the server
     * @param  reader    the buffered reader object for reading server responses
     */
    private static void handleSchoolRepOptions(Scanner scanner, PrintWriter writer, BufferedReader reader) {
        String text;
        do {
            System.out.print("Choose an option: ");
            text = scanner.nextLine();

            switch (text) {
                case "1":
                    viewApplicants(scanner,writer, reader);
                    break;
                case "2":
                    confirmApplicant(scanner, writer,reader);
                    break;
                case "3":
                    registerSchool(scanner, writer,reader);
                    break;
                case "4":
                    try {
                        handleMainMenuOptions(scanner, writer, reader);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    System.out.println("Invalid option");
                    continue;
            }

            // Read and display server responses
            String response;
            try {
                while ((response = reader.readLine()) != null && !response.isEmpty()) {
                    System.out.println(response);
                }
            } catch (IOException e) {
                System.out.println("Error reading response: " + e.getMessage());
            }

        } while (!text.equals("4"));
    }

    /**
     * Registers a school by prompting the user for their information and sending it to the server.
     *
     * @param  scanner         the scanner object for user input
     * @param  writer          the print writer object for sending messages to the server
     * @param  reader          the buffered reader object for receiving messages from the server
     * @return                 void
     */
    private static void registerSchool(Scanner scanner, PrintWriter writer,BufferedReader reader) {
        System.out.print("School Name: ");
        String name = scanner.nextLine();
        System.out.print("District: ");
        String district = scanner.nextLine();
        System.out.print("School Registration Number: ");
        String schoolRegNumber = scanner.nextLine();
        System.out.print("Representative Email: ");
        String representativeEmail = scanner.nextLine();
        System.out.println("Representative Name: ");
        String representativeName = scanner.nextLine();

        writer.println("registerSchool " + name + " " + district + " " + schoolRegNumber + " " + representativeEmail + " " + representativeName);

        try {
            String response;
            while ((response = reader.readLine()) != null && !response.isEmpty()) {
                System.out.println(response);
                if (response.contains("School registered successfully!")) {
                    displaySchoolRepMenu();
                    handleSchoolRepOptions(scanner, writer, reader);
                    return;
                } else {
                    handleSchoolRepOptions(scanner, writer, reader);
                }
            }
        } catch (Exception e) {
            System.out.println("Error during registration: " + e.getMessage());
        }
    }

    /**
     * Views the list of applicants by sending a request to the server and displaying the responses.
     *
     * @param  scanner         the scanner object for user input
     * @param  writer          the print writer object for sending messages to the server
     * @param  reader          the buffered reader object for receiving messages from the server
     * @throws IOException     if there is an error reading the server response
     */
    private static void viewApplicants(Scanner scanner, PrintWriter writer, BufferedReader reader) {
        writer.println("viewApplicants");
        writer.flush();
    
        try {
            String response;
            while ((response = reader.readLine()) != null) {
                if (response.equals("END_OF_RESPONSE")) {
                    break;
                }
                System.out.println(response);
            }
            displaySchoolRepMenu();
            handleSchoolRepOptions(scanner, writer, reader);
        } catch (IOException e) {
            System.out.println("Error reading response: " + e.getMessage());
        }
    }
    
    /**
     * Views the list of challenges by sending a request to the server and displaying the responses.
     *
     * @param  scanner         the scanner object for user input
     * @param  writer          the print writer object for sending messages to the server
     * @param  reader          the buffered reader object for receiving messages from the server
     * @throws IOException     if there is an error reading the server response
     */
    private static void viewChallenges(Scanner scanner, PrintWriter writer, BufferedReader reader) {
        writer.println("viewChallenges");
        writer.flush();
    
        try {
            String response;
            
            while ((response = reader.readLine()) != null) {
                if (response.equals("END_OF_CHALLENGES")) {
                    break;
                }
                System.out.println(response); // Print the response from the server
            }
            
            // Process end-of-challenges and return to participant menu
            displayParticipantMenu();
            handleParticipantOptions(scanner, writer, reader);
            
        } catch (IOException e) {
            System.out.println("Error reading response: " + e.getMessage());
        }
    }
    
    /**
     * Confirms or rejects an applicant based on user input and server responses.
     *
     * @param  scanner   the scanner object for reading user input
     * @param  writer    the print writer object for sending commands to the server
     * @param  reader    the buffered reader object for reading server responses
     * @throws IOException     if there is an error reading the server response
     */
    private static void confirmApplicant(Scanner scanner, PrintWriter writer, BufferedReader reader) {
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Confirm (yes/no): ");
        String confirm = scanner.nextLine();


        if (confirm.equalsIgnoreCase("yes")) {
            writer.println("confirm yes " + username);
        } else if (confirm.equalsIgnoreCase("no")) {
            System.out.print("Reason for rejection: ");
            String reason = scanner.nextLine();
            writer.println("confirm no " + username + " " + reason);
        } else {
            System.out.println("Invalid confirmation command.");
        }

        try {
            String response;
            while ((response = reader.readLine()) != null && !response.isEmpty()) {
                System.out.println(response);
                if (response.contains("Participant confirmed successfully!") || response.contains("Participant rejected successfully")) {
                    displaySchoolRepMenu();
                    handleSchoolRepOptions(scanner, writer, reader);
                    return;
                }
            }
        } catch (IOException e) {
            System.out.println("Error during confirmation: " + e.getMessage());
        }
    }

     /**
     * Enables a participant to attempt a challenge.
     *
     * @param  scanner   the scanner object for reading user input
     * @param  writer    the print writer object for sending commands to the server
     * @param  reader    the buffered reader object for reading server responses
     * @throws IOException     if there is an error reading the server response
     */   
    private static void attemptChallenge(Scanner scanner, PrintWriter writer, BufferedReader reader) {
        try {
            // Ask for participant username
            System.out.print("Participant Username: ");
            String participantUsername = scanner.nextLine();

            // Ask for challenge number
            System.out.print("Challenge Number: ");
            String challengeNumber = scanner.nextLine();

            // Send command and parameters to the server
            writer.println("attemptChallenge " + participantUsername + " " + challengeNumber);

            String serverResponse;
            while ((serverResponse = reader.readLine()) != null) {
                System.out.println(serverResponse);// Read server's response for question text
                if (serverResponse.startsWith("Question: ")) {
                    // Display question text
                    System.out.println(serverResponse);
                    // Ask for participant's answer
                    System.out.print("Your answer: ");
                    String answer = scanner.nextLine();
                    // Send answer to the server
                    writer.println(answer);
                } else if (serverResponse.equals("Max Attempts Reached!")) {
                    System.out.println("You have already attempted this challenge three times already.");
                    break;
                } else if (serverResponse.equals("Invalid participant username.")) {
                    break;
                } else if (serverResponse.contains("Challenge completed.")) {
                    break;
                }else if(serverResponse.contains("Invalid command")){
                    System.out.println(" ");
                    break;
                }
            }
            displayParticipantMenu();
            handleParticipantOptions(scanner, writer, reader);
        } catch (IOException e) {
            System.out.println("Error during challenge attempt: " + e.getMessage());
        }
    }

    /**
     * Handles the main menu options based on the user's input. Allows the user to register as an applicant, login as a participant, login as a school representative, or exit the program. Reads and displays server responses for each option.
     *
     * @param  scanner   the scanner object for reading user input
     * @param  writer    the print writer object for sending commands to the server
     * @param  reader    the buffered reader object for reading server responses
     * @throws IOException     if there is an error reading the server response
     */
    private static void handleMainMenuOptions(Scanner scanner, PrintWriter writer, BufferedReader reader) throws IOException {
        String text;

            do {
                displayMainMenu();
                // Get user input
                System.out.print("Choose an option: ");
                text = scanner.nextLine();

                switch (text) {
                    case "1":
                        registerApplicant(scanner, writer,reader);
                        break;
                    case "2":
                        loginParticipant(scanner, writer, reader);
                        break;
                    case "3":
                        loginSchoolRepresentative(scanner, writer, reader);
                        break;
                    case "4":
                        writer.println("Bye!");
                        break;
                    default:
                        System.out.println("Invalid option");
                        continue;
                }

                // Read and display server responses
                String response;
                while ((response = reader.readLine()) != null && !response.isEmpty()) {
                    // System.out.println(response);
                    if (response.equals("Invalid command")) {
                        System.exit(0);
                    }
                }

            } while (!text.equals("4")); 
    }

}
