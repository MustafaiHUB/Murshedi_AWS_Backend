package Hamza.GP.ChatBot;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
@Entity
public class QuestionAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 1024)  // Increase the size of the 'question' column
    private String question;

    @Column(columnDefinition = "TEXT")  // Use TEXT for longer 'answer' columns
    private String answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore // 🔥 This prevents infinite recursion when returning JSON
    @JoinColumn(name = "conversation_id", referencedColumnName = "id") // Ensure correct mapping
    private Conversation conversation;

    @Column(length = 1024)
    private String response_id;


    public void setQuestion(String question) { this.question = question; }
    public void setAnswer(String answer) { this.answer = answer; }
    public void setConversation(Conversation conversation) { this.conversation = conversation; }



    public String getAnswer() { return answer; }
    public Long getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }


    public String getResponse_id() {
        return response_id;
    }

    public void setResponse_id(String response_id) {
        this.response_id = response_id;
    }
}
