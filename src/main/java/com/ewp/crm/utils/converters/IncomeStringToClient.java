package com.ewp.crm.utils.converters;

import com.ewp.crm.models.Client;
import com.ewp.crm.models.SocialProfile;
import com.ewp.crm.service.interfaces.SocialProfileTypeService;
import com.ewp.crm.service.interfaces.VKService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class IncomeStringToClient {

    private final SocialProfileTypeService socialProfileTypeService;
    private final VKService vkService;
    private static final Logger logger = LoggerFactory.getLogger(IncomeStringToClient.class);

    @Autowired
    public IncomeStringToClient(SocialProfileTypeService socialProfileTypeService, VKService vkService) {
        this.socialProfileTypeService = socialProfileTypeService;
        this.vkService = vkService;
    }

    public Client convert(String income) {
        Client client = null;
        logger.info("Start of conversion");
        if (income != null && !income.isEmpty()) {
            String workString = prepareForm(income);
            if (income.contains("Начать обучение")) {
                client = parseClientFormOne(workString);
            } else if (income.contains("Месяц в подарок")) {
                client = parseClientFormOne(workString);
            } else if (income.contains("Остались вопросы")) {
                client = parseClientFormTwo(workString);
            } else if (income.contains("Задать вопрос")) {
                client = parseClientFormThree(workString);
            } else if (income.contains("Java Test")) {
                client = parseClientFormFour(workString);
            } else {
                logger.error("The incoming email does not match any of the templates!!!");
            }
            vkService.fillClientFromProfileVK(client);
        }
        return client;
    }

    private static String prepareForm(String text) {
        return text.substring(text.indexOf("Страница:"), text.length())
                .replaceAll("<b>|</b>|(\\r\\n|\\n)", "");
    }

    private Client parseClientFormOne(String form) {
        logger.info("Parsing FormOne...");
        Client client = new Client();
        String removeExtraCharacters = form.substring(form.indexOf("Страница"), form.length())
                .replaceAll(" ", "~")
                .replaceAll("Name~5", "Name")
                .replaceAll("Email~5", "Email")
                .replaceAll("Соц~~сеть", "Соцсеть");
        String[] createArrayFromString = removeExtraCharacters.split("<br~/>");
        Map<String, String> clientData = createMapFromClientData(createArrayFromString);

        String name = clientData.get("Name");
        String formattedName = name.replaceAll("~", " ");
        setClientName(client, formattedName);
        client.setPhoneNumber(clientData.get("Телефон").replace("~", ""));
        client.setCountry(clientData.get("Страна").replace("~", ""));
        client.setCity(clientData.get("Город").replace("~", ""));
        client.setEmail(clientData.get("Email").replace("~", ""));
        client.setClientDescriptionComment(clientData.get("Форма").replace("~", " "));
        if (clientData.containsKey("Запрос")) {
            client.setRequestFrom(clientData.get("Запрос").replace("~", ""));
        }
        if (clientData.containsKey("Соцсеть")) {
            String link = clientData.get("Соцсеть").replace("~", "");
            SocialProfile currentSocialProfile = getSocialNetwork(link);
            if (currentSocialProfile.getSocialProfileType().getName().equals("unknown")) {
                client.setComment("Ссылка на социальную сеть " + link +
                        " недействительна");
                logger.warn("Unknown social network");
            }
            client.setSocialProfiles(Collections.singletonList(currentSocialProfile));
        }
        logger.info("FormOne parsing finished");
        return client;
    }

    private Client parseClientFormTwo(String form) {
        logger.info("Parsing FormTwo...");
        Client client = new Client();
        String removeExtraCharacters = form.substring(form.indexOf("Форма"), form.length())
                .replaceAll(" ", "~")
                .replaceAll("Name~3", "Name");
        String[] createArrayFromString = removeExtraCharacters.split("<br~/>");
        Map<String, String> clientData = createMapFromClientData(createArrayFromString);

        String name = clientData.get("Name");
        String formattedName = name.replaceAll("~", "");
        setClientName(client, formattedName);
        client.setEmail(clientData.get("Email").replace("~", ""));
        client.setPhoneNumber(clientData.get("Phone").replace("~", ""));
        String question = clientData.get("Vopros");
        String formattedQuestion = question.replaceAll("~", " ");
        client.setClientDescriptionComment("Вопрос: " + formattedQuestion);

        checkSocialNetworks(client, clientData);
        logger.info("FormTwo parsing finished");
        return client;
    }

    private Client parseClientFormThree(String form) {
        logger.info("Parsing FormThree...");
        Client client = new Client();
        String removeExtraCharacters = form.substring(form.indexOf("Форма"), form.length())
                .replaceAll(" ", "~")
                .replaceAll("Name~3", "Name")
                .replaceAll("Phone~6", "Phone")
                .replaceAll("Email~2", "Email")
                .replaceAll("Social~2", "Social");

        String[] createArrayFromString = removeExtraCharacters.split("<br~/>");
        Map<String, String> clientData = createMapFromClientData(createArrayFromString);
        String name = clientData.get("Name");
        String formattedName = name.replaceAll("~", " ");
        setClientName(client, formattedName);
        client.setEmail(clientData.get("Email").replace("~", ""));
        client.setPhoneNumber(clientData.get("Phone").replace("~", ""));
        String question = clientData.get("Вопрос");
        String formattedQuestion = question.replaceAll("~", " ");
        client.setClientDescriptionComment("Вопрос: " + formattedQuestion);

        checkSocialNetworks(client, clientData);
        logger.info("Form Three parsing finished");
        return client;
    }

    private Client parseClientFormFour(String form) {
        logger.info("Parsing FormFour...");
        Client client = new Client();
        String removeExtraCharacters = form.substring(form.indexOf("Страница"), form.length())
                .replaceAll(" ", "~")
                .replaceAll("Email~2", "Email")
                .replaceAll("Phone~6", "Phone")
                .replaceAll("City~6", "Country");
        String[] createArrayFromString = removeExtraCharacters.split("<br~/>");
        Map<String, String> clientData = createMapFromClientData(createArrayFromString);

        String name = clientData.get("Имя");
        String formattedName = name.replaceAll("~", " ");
        setClientName(client, formattedName);
        client.setPhoneNumber(clientData.get("Phone").replace("~", ""));
        client.setCountry(clientData.get("Country").replace("~", ""));
        client.setEmail(clientData.get("Email").replace("~", ""));
        client.setClientDescriptionComment(clientData.get("Форма").replace("~", " "));
        if (clientData.containsKey("Запрос")) {
            client.setRequestFrom(clientData.get("Запрос").replace("~", ""));
        }

        checkSocialNetworks(client, clientData);
        logger.info("Form Four parsing finished");
        return client;
    }

    private void checkSocialNetworks(Client client, Map<String, String> clientData) {
        if (clientData.containsKey("Social")) {
            String link = clientData.get("Social").replace("~", "");
            if (link != null && !link.isEmpty()) {
                SocialProfile currentSocialProfile = getSocialNetwork(link);
                if (currentSocialProfile.getSocialProfileType().getName().equals("unknown")) {
                    client.setComment(String.format("Ссылка на социальную сеть %s недействительна", link));
                    logger.warn("Unknown social network '" + link + "'");
                } else {
                    client.setSocialProfiles(Collections.singletonList(currentSocialProfile));
                }
            }
        }
    }

    private SocialProfile getSocialNetwork(String link) {
        SocialProfile socialProfile = new SocialProfile();
        if (link.contains("vk.com") || link.contains("m.vk.com")) {
            String validLink = vkService.refactorAndValidateVkLink(link);
            if (validLink.equals("undefined")) {
                socialProfileTypeService.getByTypeName("unknown").ifPresent(socialProfile::setSocialProfileType);
            } else {
                Optional<String> socialId = vkService.getIdFromLink(link);
                if (socialId.isPresent()) {
                    socialProfile.setSocialId(socialId.get());
                    socialProfileTypeService.getByTypeName("vk").ifPresent(socialProfile::setSocialProfileType);
                }
            }
        } else if (link.contains("www.facebook.com") || link.contains("m.facebook.com")) {
            socialProfileTypeService.getByTypeName("facebook").ifPresent(socialProfile::setSocialProfileType);
        } else {
            socialProfileTypeService.getByTypeName("unknown").ifPresent(socialProfile::setSocialProfileType);
        }
        return socialProfile;
    }

    private Map<String, String> createMapFromClientData(String[] res) {
        Map<String, String> clientData = new HashMap<>();
        for (String re : res) {
            String name = re.substring(0, re.indexOf(":"));
            String value = re.substring(re.indexOf(":") + 1, re.length());
            clientData.put(name, value);
        }
        return clientData;
    }

    private void setClientName(Client client, String fullName) {
        if (StringUtils.countOccurrencesOf(fullName, " ") == 1) {
            String[] full = fullName.split(" ");
            client.setName(full[0]);
            client.setLastName(full[1]);
        } else {
            client.setName(fullName);
        }
    }
}