package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.config.CasConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Service
public class CasService {

    private static final Logger log = LoggerFactory.getLogger(CasService.class);

    private final CasConfig casConfig;
    private final RestClient restClient;
    private final XMLInputFactory xmlInputFactory;

    public CasService(CasConfig casConfig) {
        this.casConfig = casConfig;
        this.restClient = RestClient.builder().build();
        this.xmlInputFactory = XMLInputFactory.newInstance();
        // prevent XXE
        this.xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        this.xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    /**
     * Validates a CAS service ticket and extracts user attributes.
     *
     * @param ticket  the CAS service ticket (ST-xxxx)
     * @param service the service URL that issued the ticket
     * @return CasUserAttributes with cas_id, username, email
     * @throws BusinessException if ticket is invalid or CAS server is unreachable
     */
    public CasUserAttributes validateTicket(String ticket, String service) {
        String validateUrl = casConfig.getServiceValidateUrl()
                + "?ticket=" + ticket + "&service=" + service;

        String responseBody;
        try {
            responseBody = restClient.get()
                    .uri(validateUrl)
                    .retrieve()
                    .onStatus(status -> status.value() >= 500,
                            (req, resp) -> {
                                throw new BusinessException(ErrorCode.UNKNOWN, "CAS 服务暂不可用");
                            })
                    .body(String.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CAS server unreachable: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNKNOWN, "CAS 服务暂不可用，请使用邮箱登录");
        }

        return parseServiceResponse(responseBody);
    }

    /**
     * Validates a CAS authorization code for account binding.
     */
    public CasUserAttributes validateCode(String code, String redirectUri) {
        // For CAS protocol, the bind flow uses the same ticket validation mechanism.
        // The code is treated as a service ticket, and the redirect_uri as the service.
        return validateTicket(code, redirectUri);
    }

    private CasUserAttributes parseServiceResponse(String xml) {
        try {
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new StringReader(xml));

            boolean authSuccess = false;
            String casId = null;
            String username = null;
            String email = null;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    switch (localName) {
                        case "authenticationSuccess":
                            authSuccess = true;
                            break;
                        case "authenticationFailure":
                            throw new BusinessException(ErrorCode.UNAUTHORIZED, "CAS ticket 无效");
                        case "user":
                            casId = reader.getElementText();
                            break;
                        case "cas:user":
                            casId = reader.getElementText();
                            break;
                        case "uid":
                            if (casId == null) casId = reader.getElementText();
                            break;
                        case "username":
                            username = reader.getElementText();
                            break;
                        case "cas:username":
                            username = reader.getElementText();
                            break;
                        case "email":
                            email = reader.getElementText();
                            break;
                        case "cas:email":
                            email = reader.getElementText();
                            break;
                        case "mail":
                            if (email == null) email = reader.getElementText();
                            break;
                    }
                }
            }
            reader.close();

            if (!authSuccess) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "CAS ticket 无效");
            }

            return new CasUserAttributes(casId != null ? casId : username, username, email);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse CAS response: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNKNOWN, "CAS 响应解析失败");
        }
    }

    /**
     * Internal record for CAS user attributes extracted from service validation response.
     */
    public record CasUserAttributes(String casId, String username, String email) {}
}
