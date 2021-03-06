package com.ewp.crm.component;

import com.ewp.crm.exceptions.member.NotFoundMemberList;
import com.ewp.crm.exceptions.parse.ParseClientException;
import com.ewp.crm.exceptions.util.FBAccessTokenException;
import com.ewp.crm.exceptions.util.VKAccessTokenException;
import com.ewp.crm.models.*;
import com.ewp.crm.service.email.MailingService;
import com.ewp.crm.service.interfaces.*;
import com.ewp.crm.service.interfaces.vkcampaigns.VkCampaignService;
import com.ewp.crm.utils.patterns.ValidationPattern;
import org.drinkless.tdlib.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@EnableScheduling
@PropertySource(value = "file:./skype-message.properties", encoding = "Cp1251")
@PropertySource(value = "file:./advertisement-report.properties", encoding = "UTF-8")
public class ScheduleTasks {

    private final VKService vkService;

	private final PotentialClientService potentialClientService;

	private final YouTubeTrackingCardService youTubeTrackingCardService;

	private final ClientService clientService;

	private final StudentService studentService;

	private final StatusService statusService;

	private final SocialProfileService socialProfileService;

	private final SocialProfileTypeService socialProfileTypeService;

	private final SMSService smsService;

	private final SMSInfoService smsInfoService;

	private final MailSendService mailSendService;

	private final SendNotificationService sendNotificationService;

	private final ClientHistoryService clientHistoryService;

	private final FacebookService facebookService;

	private final VkTrackedClubService vkTrackedClubService;

	private final VkMemberService vkMemberService;

	private final YoutubeService youtubeService;

	private final YoutubeClientService youtubeClientService;

	private final AssignSkypeCallService assignSkypeCallService;

	private final ReportService reportService;

	private final ProjectPropertiesService projectPropertiesService;

	private Environment env;

	private final MailingService mailingService;

	private final VkCampaignService vkCampaignService;

    private final TelegramService telegramService;

    private final SlackService slackService;

	private static Logger logger = LoggerFactory.getLogger(ScheduleTasks.class);

	private String adReportTemplate;

	@Autowired
	public ScheduleTasks(VKService vkService, PotentialClientService potentialClientService,
						 YouTubeTrackingCardService youTubeTrackingCardService,
						 ClientService clientService, StudentService studentService,
						 StatusService statusService, ProjectPropertiesService projectPropertiesService,
						 MailingService mailingService, SocialProfileService socialProfileService,
						 SocialProfileTypeService socialProfileTypeService, SMSService smsService,
						 SMSInfoService smsInfoService, SendNotificationService sendNotificationService,
						 ClientHistoryService clientHistoryService, VkTrackedClubService vkTrackedClubService,
						 VkMemberService vkMemberService, FacebookService facebookService, YoutubeService youtubeService,
						 YoutubeClientService youtubeClientService, AssignSkypeCallService assignSkypeCallService,
						 MailSendService mailSendService, Environment env, ReportService reportService,
						 VkCampaignService vkCampaignService, TelegramService telegramService,
						 SlackService slackService) {
		this.vkService = vkService;
		this.potentialClientService = potentialClientService;
		this.youTubeTrackingCardService = youTubeTrackingCardService;
		this.clientService = clientService;
		this.studentService = studentService;
		this.statusService = statusService;
		this.socialProfileService = socialProfileService;
		this.socialProfileTypeService = socialProfileTypeService;
		this.smsService = smsService;
		this.smsInfoService = smsInfoService;
		this.mailSendService = mailSendService;
		this.sendNotificationService = sendNotificationService;
		this.clientHistoryService = clientHistoryService;
		this.facebookService = facebookService;
		this.vkTrackedClubService = vkTrackedClubService;
		this.vkMemberService = vkMemberService;
		this.youtubeService = youtubeService;
		this.youtubeClientService = youtubeClientService;
		this.assignSkypeCallService = assignSkypeCallService;
		this.reportService = reportService;
		this.env = env;
		this.mailingService = mailingService;
		this.projectPropertiesService = projectPropertiesService;
		this.vkCampaignService = vkCampaignService;
		this.adReportTemplate = env.getProperty("template.daily.report");
		this.telegramService = telegramService;
		this.slackService = slackService;
	}

	private void addClient(Client newClient) {
		statusService.getFirstStatusForClient().ifPresent(newClient::setStatus);
		newClient.setState(Client.State.NEW);
		socialProfileTypeService.getByTypeName("vk").ifPresent(newClient.getSocialProfiles().get(0)::setSocialProfileType);
		clientHistoryService.createHistory("vk").ifPresent(newClient::addHistory);
		vkService.fillClientFromProfileVK(newClient);
		String email = newClient.getEmail();
		if (email!=null&&!email.matches(ValidationPattern.EMAIL_PATTERN)){
			newClient.setClientDescriptionComment(newClient.getClientDescriptionComment()+System.lineSeparator()+"Возможно клиент допустил ошибку в поле Email: "+email);
			newClient.setEmail(null);
		}
		clientService.addClient(newClient);
		sendNotificationService.sendNewClientNotification(newClient, "vk");
		logger.info("New client with id{} has added from VK", newClient.getId());
	}

	@Scheduled(fixedRate = 15_000)
	private void checkCallInSkype() {
		for (AssignSkypeCall assignSkypeCall : assignSkypeCallService.getAssignSkypeCallIfCallDateHasAlreadyPassedButHasNotBeenClearedToTheClient()) {
			Client client = assignSkypeCall.getToAssignSkypeCall();
			client.setLiveSkypeCall(false);
			assignSkypeCall.setSkypeCallDateCompleted(true);
			clientService.updateClient(client);
			assignSkypeCallService.update(assignSkypeCall);
		}
	}

	@Scheduled(fixedRate = 30_000)
	private void checkCallInSkypeToSendTheNotification() {
		for (AssignSkypeCall assignSkypeCall : assignSkypeCallService.getAssignSkypeCallIfNotificationWasNoSent()) {
			Client client = assignSkypeCall.getToAssignSkypeCall();
			String skypeTemplateHtml = env.getRequiredProperty("skype.template");
			String skypeTemplateText = env.getRequiredProperty("skype.textTemplate");
			User principal = assignSkypeCall.getFromAssignSkypeCall();
			Long clientId = client.getId();
			String dateOfSkypeCall = ZonedDateTime.parse(assignSkypeCall.getNotificationBeforeOfSkypeCall().toString())
					.plusHours(1).format(DateTimeFormatter.ofPattern("dd MMMM в HH:mm по МСК"));
			sendNotificationService.sendNotificationType(dateOfSkypeCall, client, principal, Notification.Type.ASSIGN_SKYPE);
			if (clientService.hasClientSocialProfileByType(client, "vk")) {
				try {
					vkService.sendMessageToClient(clientId, skypeTemplateText, dateOfSkypeCall, principal);
				} catch (Exception e) {
					logger.warn("VK message not sent", e);
				}
			}
			if (client.getPhoneNumber() != null && !client.getPhoneNumber().isEmpty()) {
				try {
					smsService.sendSMS(clientId, skypeTemplateText, dateOfSkypeCall, principal);
				} catch (Exception e) {
					logger.warn("SMS message not sent", e);
				}
			}
			if (client.getEmail() != null && !client.getEmail().isEmpty()) {
				try {
					mailSendService.prepareAndSend(clientId, skypeTemplateHtml, dateOfSkypeCall, principal);
				} catch (Exception e) {
					logger.warn("E-mail message not sent");
				}
			}
			assignSkypeCall.setTheNotificationWasIsSent(true);
			assignSkypeCallService.update(assignSkypeCall);
		}
	}

	@Scheduled(fixedRate = 5_000)
	private void handleRequestsFromVk() {
		if (vkService.hasTechnicalAccountToken()) {
			try {
				Optional<List<String>> newMassages = vkService.getNewMassages();
				if (newMassages.isPresent()) {
					for (String message : newMassages.get()) {
						try {
							Client newClient = vkService.parseClientFromMessage(message);
							String s = newMassages.orElse(Collections.emptyList()).toString().replaceAll("<br><br>","<br>");
							ClientHistory clientHistory = new ClientHistory(s,ZonedDateTime.now(ZoneId.systemDefault()),ClientHistory.Type.SOCIAL_REQUEST);
							newClient.addHistory(clientHistory);
							addClient(newClient);
						} catch (ParseClientException e) {
							logger.error(e.getMessage());
						}
					}
				}
			} catch (VKAccessTokenException ex) {
				logger.error(ex.getMessage());
			}
		}
	}

	@Scheduled(fixedRate = 60_000)
	private void findNewMembersAndSendFirstMessage() {
		List<VkTrackedClub> vkTrackedClubList = vkTrackedClubService.getAll();
		List<VkMember> lastMemberList = vkMemberService.getAll();
		for (VkTrackedClub vkTrackedClub : vkTrackedClubList) {
			List<VkMember> freshMemberList = vkService.getAllVKMembers(vkTrackedClub.getGroupId(), 0L)
					.orElseThrow(NotFoundMemberList::new);
			int countNewMembers = 0;
			for (VkMember vkMember : freshMemberList) {
				if (!lastMemberList.contains(vkMember)) {
					vkService.sendMessageById(vkMember.getVkId(), vkService.getFirstContactMessage());
					vkMemberService.add(vkMember);
					countNewMembers++;
				}
			}
			if (countNewMembers > 0) {
				logger.info("{} new VK members has signed in {} club", countNewMembers, vkTrackedClub.getGroupName());
			}
		}
	}

	@Scheduled(fixedRate = 5_000)
	private void handleRequestsFromVkCommunityMessages() {
		Optional<List<Long>> newUsers = vkService.getUsersIdFromCommunityMessages();
		if (newUsers.isPresent()) {
			for (Long id : newUsers.get()) {
				Optional<Client> newClient = vkService.getClientFromVkId(id);
				if (newClient.isPresent()) {
					SocialProfile socialProfile = newClient.get().getSocialProfiles().get(0);
					if (!(socialProfileService.getSocialProfileBySocialIdAndSocialType(socialProfile.getSocialId(), "vk").isPresent())) {
						addClient(newClient.get());
					}
				}
			}
		}
	}

	@Scheduled(fixedRate = 6_000)
	private void checkClientActivationDate() {
		for (Client client : clientService.getChangeActiveClients()) {
			client.setPostponeDate(null);
			client.setHideCard(false);
			sendNotificationService.sendNotificationType(client.getClientDescriptionComment(), client, client.getOwnerUser(), Notification.Type.POSTPONE);
			clientService.updateClient(client);
		}
	}

	@Scheduled(fixedDelay = 6_000)
	private void sendMailing() {
        mailingService.sendMessages();
	}

	@Scheduled(cron = "* */15 * * * *")
	private void getSlackProfiles() {
		slackService.tryLinkSlackAccountToAllStudents();
	}

	@Scheduled(fixedRate = 600_000)
	private void addFacebookMessageToDatabase() {
		try {
			facebookService.getFacebookMessages();
		} catch (FBAccessTokenException e) {
			logger.error("Facebook access token has not got", e);
		}
	}

	@Scheduled(cron = "0 0 0 * * *")
	private void sendDailyAdvertisementReport() {
        LocalDate date = LocalDate.now().minusDays(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String formattedDate = date.format(formatter);
        adReportTemplate = adReportTemplate.replace("{date}", formattedDate);
        vkService.sendDailyAdvertisementReport(adReportTemplate);
	}

	@Scheduled(cron = "0 0 10 01 * ?")
	private void buildAndSendReport() {
		// ToDo рассылка отчета
	}

	@Scheduled(fixedRate = 600_000)
	private void checkSMSMessages() {
		logger.info("start checking sms statuses");
		List<SMSInfo> queueSMS = smsInfoService.getSMSByIsChecked(false);
		for (SMSInfo sms : queueSMS) {
			Optional<String> status = smsService.getStatusMessage(sms.getSmsId());
			if (status.isPresent()) {
				if (!status.get().equals("queued")) {
					if (status.get().equals("delivered")) {
						sms.setDeliveryStatus("доставлено");
					} else if (sms.getClient() == null) {
						logger.error("Can not create notification with empty SMS client, SMS message: {}", sms);
						sms.setDeliveryStatus("Клиент не найден");
					} else {
						String deliveryStatus = determineStatusOfResponse(status.get());
						sendNotificationService.sendNotificationType(deliveryStatus, sms.getClient(), sms.getUser(), Notification.Type.SMS);
						sms.setDeliveryStatus(deliveryStatus);
					}
					sms.setChecked(true);
					smsInfoService.update(sms);
				}
			}
		}
	}

	private String determineStatusOfResponse(String status) {
		String info;
		switch (status) {
			case "delivery error":
				info = "Номер заблокирован или вне зоны";
				break;
			case "invalid mobile phone":
				info = "Неправильный формат номера";
				break;
			case "incorrect id":
				info = "Неверный id сообщения";
				break;
			default:
				info = "Неизвестная ошибка";
		}
		return info;
	}

	@Scheduled(fixedRate = 60_000)
	private void handleYoutubeLiveStreams() {
		for (YouTubeTrackingCard youTubeTrackingCard : youTubeTrackingCardService.getAllByHasLiveStream(false)) {
			youtubeService.handleYoutubeLiveChatMessages(youTubeTrackingCard);
		}
	}

	@Scheduled(fixedRate = 60_000)
	private void getPotentialClientsFromYoutubeClients() {
		for (YoutubeClient youtubeClient : youtubeClientService.getAllByChecked(false)) {
			Optional<PotentialClient> newPotentialClient = vkService.getPotentialClientFromYoutubeLiveStreamByYoutubeClient(youtubeClient);
			if (newPotentialClient.isPresent()) {
				SocialProfile socialProfile = newPotentialClient.get().getSocialProfiles().get(0);
				if (!socialProfileService.getSocialProfileBySocialIdAndSocialType(socialProfile.getSocialId(), "vk").isPresent()) {
					potentialClientService.addPotentialClient(newPotentialClient.get());
				}
			}
		}
	}

	/**
	 * Sends payment notification to student's contacts.
	 */
	@Scheduled(fixedDelay = 3600000)
	private void sendPaymentNotifications() {
		ProjectProperties properties = projectPropertiesService.getOrCreate();
		if (properties.isPaymentNotificationEnabled() && properties.getPaymentMessageTemplate() != null && properties.getPaymentNotificationTime() != null) {
			LocalTime time = properties.getPaymentNotificationTime().truncatedTo(ChronoUnit.HOURS);
			LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.HOURS);
			if (properties.isPaymentNotificationEnabled() && now.equals(time)) {
				for (Student student : studentService.getStudentsWithTodayNotificationsEnabled()) {
					MessageTemplate template = properties.getPaymentMessageTemplate();
					Long clientId = student.getClient().getId();
					if (student.isNotifyEmail()) {
						mailSendService.sendSimpleNotification(clientId, template.getTemplateText());
					}
					if (student.isNotifySMS()) {
						smsService.sendSimpleSMS(clientId, template.getOtherText());
					}
					if (student.isNotifyVK()) {
						vkService.simpleVKNotification(clientId, template.getOtherText());
					}
					if (student.isNotifySlack()) {
						slackService.trySendSlackMessageToStudent(student.getId(), template.getOtherText());
					}
				}
			}
		} else {
			logger.info("Payment notification properties not set!");
		}
	}

    @Scheduled(fixedRate = 60_000)
    private void fetchTelegramMessages() {
        if (telegramService.isTdlibInstalled() && telegramService.isAuthenticated()) {
            TdApi.Chats chats = telegramService.getChats();
            for (int i = 0; i < chats.chatIds.length; i++) {
                telegramService.getUnreadMessagesFromChat(chats.chatIds[i], 1);
            }
        }
    }

	/**
	 * Sends friend requests from all VK friends campaigns
	 * once per 36 minutes == 40 per day (VK day limit) 2_160_000
	 */
	@Scheduled(fixedRate = 2_160_000)
	private void addOneFriendForEachCampaign() {
		logger.info("Scheduled task to add next VK friend for all campaigns been fired");
		vkCampaignService.nextAttemptCycle();
	}
}