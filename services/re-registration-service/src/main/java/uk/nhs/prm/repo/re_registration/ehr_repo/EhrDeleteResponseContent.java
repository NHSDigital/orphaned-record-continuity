package uk.nhs.prm.repo.re_registration.ehr_repo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EhrDeleteResponseContent {
    public String type;
    public String id;
    public List<String> conversationIds;
}
