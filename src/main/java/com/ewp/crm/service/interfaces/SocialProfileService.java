package com.ewp.crm.service.interfaces;

import com.ewp.crm.models.Client;
import com.ewp.crm.models.SocialProfile;

import java.util.Optional;

public interface SocialProfileService {

	Optional<SocialProfile> getSocialProfileBySocialIdAndSocialType(String id, String socialType);

	Optional<SocialProfile> getSocialProfileByClientIdAndTypeName(long clientId, String profileName);

	Optional<String> getClientSocialProfileLinkByTypeName(Client client, String typeName);
}
