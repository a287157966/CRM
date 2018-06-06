package com.ewp.crm.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "comment_answer")
public class CommentAnswer {

	@Column(name = "answer_id")
	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(targetEntity = User.class)
	@JoinTable(name = "user_id", foreignKey = @ForeignKey(name = "FK_USER"))
	private User user;

	@JsonIgnore
	@ManyToOne(targetEntity = Client.class)
	@JoinTable(name = "client_comment_answer",
			joinColumns = {@JoinColumn(name = "answer_id", foreignKey = @ForeignKey(name = "FK_COMMENT_ANSWER_CLIENT"))},
			inverseJoinColumns = {@JoinColumn(name = "client_id", foreignKey = @ForeignKey(name = "FK_COMMENT_ANSWER"))})
	private Client client;

	@Column
	private Date date = new Date();

	@Column
	private String content;

	@JsonIgnore
	@ManyToOne
	@JoinTable(name = "comment_comment_answer",
			joinColumns = {@JoinColumn(name = "answer_id", foreignKey = @ForeignKey(name = "FK_COMMENT_ANSWER"))},
			inverseJoinColumns = {@JoinColumn(name = "comment_id", foreignKey = @ForeignKey(name = "FK_ANSWER"))})
	private Comment mainComment;


	public CommentAnswer() {
	}

	public CommentAnswer(User user, String content, Client client) {
		this.user = user;
		this.content = content;
		this.client = client;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Comment getMainComment() {
		return mainComment;
	}

	public void setMainComment(Comment mainComment) {
		this.mainComment = mainComment;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public Client getClient() {
		return client;
	}

	public void setClient(Client client) {
		this.client = client;
	}
}
