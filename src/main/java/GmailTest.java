package src.main.java;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.ModifyMessageRequest;

import org.apache.log4j.Logger;

public class GmailTest {
    private static final Logger log = Logger.getLogger(GmailTest.class.getName());

    /** Application name. */
    private static final String APPLICATION_NAME = "Gmail API";

    /** Directory to store user credentials for this application. */
    private static final java.io.File DATA_STORE_DIR =
        new java.io.File(System.getProperty("user.home"),
                         ".credentials/gmail-java-quickstart.json");

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** Global instance of the scopes required by this quickstart.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/gmail-java-quickstart.json
     */
    //private final List<String> SCOPES = Arrays.asList(GmailScopes.GMAIL_LABELS);
    private static final List<String> SCOPES = Arrays.asList(GmailScopes.GMAIL_LABELS,
                                                             GmailScopes.GMAIL_COMPOSE,
                                                             GmailScopes.GMAIL_INSERT,
                                                             GmailScopes.GMAIL_MODIFY,
                                                             GmailScopes.GMAIL_READONLY,
                                                             GmailScopes.MAIL_GOOGLE_COM);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        }
        catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            // Build a new authorized API client service.
            Gmail service = getGmailService();

            // Print the labels in the user's account.
            String user = "me";
            ListLabelsResponse listResponse = service.users().labels().list(user).execute();
            List<Label> labels = listResponse.getLabels();
            if (labels == null || labels.size() <= 0) {
                log.info("No labels found.");
            }
            else {
                log.info("Labels:");
                for (Label label : labels) {
                    log.info("- " + label.getName() + " (" + label.getId() + ")\n");
                }

                // Retrieve daily arrest email in the inbox folder.
                List<String> labelIds = Arrays.asList("INBOX");
                //List<String> labelIds = new ArrayList<String>();
                //labelIds.add("INBOX");
                String query = "from:JWebEmail@sdsheriff.org";
                List<Message> messages = listMessagesMatchingQuery(service, user, labelIds, query);
                if (messages == null || messages.size() <= 0) {
                    log.info("No messages found.");
                }
                else {
                    log.info("\nArrest messages from inbox:");
                    for (Message msg : messages) {
                        // Retrieve email message and attachment.
                        getMessage(service, user, msg.getId());

                        // Move email message.
                        log.info("Move message to the Sheriff's Data folder.");
                        List<String> labelsToAdd = Arrays.asList("Label_2");
                        modifyMessage(service, user, msg.getId(), labelsToAdd, labelIds);
                    }
                }
            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Build and return an authorized Gmail client service.
     * @return an authorized Gmail client service
     * @throws IOException
     */
    private static Gmail getGmailService() throws IOException {
        Credential credential = authorize();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
            .setApplicationName(APPLICATION_NAME)
            .build();
    }

    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException
     */
    private static Credential authorize() throws IOException {
        // Load client secrets.
        InputStream in = GmailTest.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                                                                     new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
            new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                                                    clientSecrets, SCOPES)
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build();
        Credential credential =
            new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        return credential;
    }

    /**
    * List all Messages of the user's mailbox matching the query.
    *
    * @param service Authorized Gmail API instance.
    * @param userId User's email address. The special value "me"
    * can be used to indicate the authenticated user.
    * @param query String used to filter the Messages listed.
    * @throws IOException
    */
    private static List<Message> listMessagesMatchingQuery(Gmail service, String userId,
                                                    List<String> labelIds,
                                                    String query) throws IOException {
        ListMessagesResponse response = service.users()
                                               .messages()
                                               .list(userId)
                                               .setLabelIds(labelIds)
                                               .setQ(query)
                                               .execute();
        List<Message> messages = new ArrayList<Message>();
        while (response.getMessages() != null) {
            messages.addAll(response.getMessages());
            if (response.getNextPageToken() != null) {
                String pageToken = response.getNextPageToken();
                response = service.users()
                                  .messages()
                                  .list(userId)
                                  .setQ(query)
                                  .setPageToken(pageToken)
                                  .execute();
            }
            else {
                break;
            }
        }
        return messages;
    }

    /**
       * Get Message with given ID.
       *
       * @param service Authorized Gmail API instance.
       * @param userId User's email address. The special value "me"
       * can be used to indicate the authenticated user.
       * @param messageId ID of Message to retrieve.
       * @return Message Retrieved Message.
       * @throws IOException
       */
    private static Message getMessage(Gmail service, String userId, String messageId) throws IOException {
        Message message = service.users().messages().get(userId, messageId).execute();
        List<MessagePart> parts = message.getPayload().getParts();
        log.info("Message snippet: " + message.getSnippet());
        for (int i = 0; i < parts.size(); i++) {
            MessagePart mp = parts.get(i);
            log.info("--------------------");
            log.info("part[" + i + "]");
            log.info("id: " + mp.getPartId());
            log.info("mime type: " + mp.getMimeType());

            String filename = mp.getFilename();
            if (filename != null && filename.length() > 0) {
                // Download attachment.
                log.info("file name: " + mp.getFilename());
                String attId = mp.getBody().getAttachmentId();
                if (attId != null && attId.length() > 0) {
                    log.info("attachment id: " + attId);
                    MessagePartBody attachPart = service.users().messages()
                                                                .attachments()
                                                                .get(userId, messageId, attId)
                                                                .execute();
                    byte[] fileByteArray = Base64.decodeBase64(attachPart.getData());
                    //String saveFilename = "/opt/cie/JailDataService/Arrests_Data.pdf";
                    String saveFilename = "../JailData/Arrests_Data.pdf";
                    FileOutputStream fileOutFile = new FileOutputStream(saveFilename);
                    fileOutFile.write(fileByteArray);
                    fileOutFile.close();
                }
            }
        }
        return message;
    }

    /**
    * Modify the labels a message is associated with.
    *
    * @param service Authorized Gmail API instance.
    * @param userId User's email address. The special value "me"
    * can be used to indicate the authenticated user.
    * @param messageId ID of Message to Modify.
    * @param labelsToAdd List of label ids to add.
    * @param labelsToRemove List of label ids to remove.
    * @throws IOException
    */
    private static void modifyMessage(Gmail service, String userId, String messageId,
                               List<String> labelsToAdd, List<String> labelsToRemove)
                               throws IOException {
        ModifyMessageRequest mods = new ModifyMessageRequest().setAddLabelIds(labelsToAdd)
                                                              .setRemoveLabelIds(labelsToRemove);
        Message message = service.users()
                                 .messages()
                                 .modify(userId, messageId, mods).execute();
    }
}
