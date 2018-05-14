package com.ewp.crm.controllers.rest;

import com.ewp.crm.component.util.VKUtil;
import com.ewp.crm.component.util.interfaces.SMSUtil;
import com.ewp.crm.configs.ImageConfig;
import com.ewp.crm.models.Client;
import com.ewp.crm.models.User;
import com.ewp.crm.service.email.MailSendService;
import com.ewp.crm.service.impl.MessageTemplateServiceImpl;
import com.ewp.crm.service.interfaces.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
public class SendAllMessageController {

	private final MailSendService mailSendService;
	private final MessageTemplateServiceImpl MessageTemplateService;
	private final ClientService clientService;
	private final SMSUtil smsUtil;
	private final VKUtil vkUtil;

	@Autowired
	public SendAllMessageController(MailSendService mailSendService, MessageTemplateServiceImpl MessageTemplateService, ClientService clientService, SMSUtil smsUtil, VKUtil vkUtil) {
		this.mailSendService = mailSendService;
		this.MessageTemplateService = MessageTemplateService;
		this.clientService = clientService;
		this.smsUtil = smsUtil;
		this.vkUtil = vkUtil;
	}

	@RequestMapping(value = "/rest/messages", method = RequestMethod.POST)
	public ResponseEntity sendSeveralMessage(@RequestParam("boxList") String boxList,
	                                         @RequestParam("clientId") Long clientId, @RequestParam("templateId") Long templateId) {
		User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		Client client = clientService.getClientByID(clientId);
		if (boxList.contains("vk")) {
			String vkText = MessageTemplateService.get(templateId).getOtherText();
			String fullName = client.getName() + " " + client.getLastName();
			Map<String, String> params = new HashMap<>();
			params.put("%fullName%", fullName);
			vkUtil.sendMessageToClient(client, vkText, params, principal);

		}
		if (boxList.contains("facebook")) {
			// TODO no facebook integration
		}
		if (boxList.contains("email")) {
			String fullName = client.getName() + " " + client.getLastName();
			Map<String, String> params = new HashMap<>();
			params.put("%fullName%", fullName);
			mailSendService.prepareAndSend(client.getEmail(), params, MessageTemplateService.get(templateId).getTemplateText(),
					"emailStringTemplate");
		}
		if (boxList.contains("sms")) {
			String fullName = client.getName() + " " + client.getLastName();
			Map<String, String> params = new HashMap<>();
			params.put("%fullName%", fullName);
			String text = replaceName(MessageTemplateService.get(templateId).getOtherText(), params);
			smsUtil.sendSMS(client, text, principal);
		}
		return ResponseEntity.ok(HttpStatus.OK);
	}


	@RequestMapping(value = "/rest/messages/custom", method = RequestMethod.POST)
	public ResponseEntity sendSeveralMessage(@RequestParam("boxList") String boxList,
	                                         @RequestParam("clientId") Long clientId, @RequestParam("body") String body) {
		User principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (boxList.contains("vk")) {
			Client client = clientService.getClientByID(clientId);
			String vkText = MessageTemplateService.get(1L).getOtherText();
			Map<String, String> params = new HashMap<>();
			params.put("%bodyText%", body);
			vkUtil.sendMessageToClient(client, vkText, params, principal);

		}
		if (boxList.contains("facebook")) {
			System.out.println("FBесть!");
		}
		if (boxList.contains("email")) {
			Client client = clientService.getClientByID(clientId);
			Map<String, String> params = new HashMap<>();
			params.put("%bodyText%", body);
			mailSendService.prepareAndSend(client.getEmail(), params, MessageTemplateService.get(1L).getTemplateText(),
					"emailStringTemplate");
		}
		if (boxList.contains("sms")) {
			System.out.println("SMSесть!");
		}
		return ResponseEntity.ok(HttpStatus.OK);
	}

	private String replaceName(String msg, Map<String, String> params) {
		String vkText = msg;
		for (Map.Entry<String, String> entry : params.entrySet()) {
			vkText = String.valueOf(new StringBuilder(vkText.replaceAll(entry.getKey(), entry.getValue())));
		}
		return vkText;
	}
}