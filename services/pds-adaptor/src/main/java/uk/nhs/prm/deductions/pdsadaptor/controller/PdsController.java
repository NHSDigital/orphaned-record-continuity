package uk.nhs.prm.deductions.pdsadaptor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import uk.nhs.prm.deductions.pdsadaptor.configuration.Tracer;
import uk.nhs.prm.deductions.pdsadaptor.model.SuspendedPatientStatus;
import uk.nhs.prm.deductions.pdsadaptor.model.UpdateManagingOrganisationRequest;
import uk.nhs.prm.deductions.pdsadaptor.service.PdsService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.security.Principal;

@RestController
@RequestMapping("suspended-patient-status")
@RequiredArgsConstructor
@Slf4j
public class PdsController {

    private final PdsService pdsService;
    private final Tracer tracer;

    @Operation(security = @SecurityRequirement(name = "basicAuth"))
    @GetMapping("/{nhsNumber}")
    @ResponseStatus(HttpStatus.OK)
    public SuspendedPatientStatus getPatientGpStatus(
            @PathVariable("nhsNumber") @NotBlank @Size(max = 10, min = 10) String nhsNumber,
            @RequestHeader(value = "traceId", required = false) String traceId,
            Principal principal
    ) {
        tracer.setTraceId(traceId);
        log.info("Request for pds record received from {}", principal.getName());
        return pdsService.getPatientGpStatus(nhsNumber);
    }

    @Operation(security = @SecurityRequirement(name = "basicAuth"))
    @PutMapping("/{nhsNumber}")
    @ResponseStatus(HttpStatus.OK)
    public SuspendedPatientStatus updatePatientManagingOrganisation(
            @PathVariable("nhsNumber") @NotBlank @Size(max = 10, min = 10) String nhsNumber,
            @RequestBody UpdateManagingOrganisationRequest updateRequest,
            @RequestHeader(value = "traceId", required = false) String traceId,
            Principal principal
    ) {
        tracer.setTraceId(traceId);
        log.info("Update request for pds record received from {}", principal.getName());
        return pdsService.updatePatientManagingOrganisation(nhsNumber, updateRequest);
    }
}
