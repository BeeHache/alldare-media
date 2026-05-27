package online.alldare.media.domain.dto;

import java.net.URL;

public record StorageResponse(URL uploadUrl, String fileName) {
}
