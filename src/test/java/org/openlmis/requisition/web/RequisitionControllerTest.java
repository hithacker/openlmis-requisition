/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org. 
 */

package org.openlmis.requisition.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openlmis.requisition.domain.Requisition;
import org.openlmis.requisition.domain.RequisitionStatus;
import org.openlmis.requisition.domain.RequisitionTemplate;
import org.openlmis.requisition.dto.BasicRequisitionTemplateDto;
import org.openlmis.requisition.dto.FacilityDto;
import org.openlmis.requisition.dto.ProcessingPeriodDto;
import org.openlmis.requisition.dto.ProgramDto;
import org.openlmis.requisition.dto.RequisitionDto;
import org.openlmis.requisition.dto.SupervisoryNodeDto;
import org.openlmis.requisition.dto.UserDto;
import org.openlmis.requisition.errorhandling.ValidationResult;
import org.openlmis.requisition.exception.BindingResultException;
import org.openlmis.requisition.exception.ValidationMessageException;
import org.openlmis.requisition.repository.RequisitionRepository;
import org.openlmis.requisition.repository.RequisitionTemplateRepository;
import org.openlmis.requisition.service.PeriodService;
import org.openlmis.requisition.service.PermissionService;
import org.openlmis.requisition.service.RequisitionService;
import org.openlmis.requisition.service.RequisitionStatusNotifier;
import org.openlmis.requisition.service.RequisitionStatusProcessor;
import org.openlmis.requisition.service.referencedata.OrderableReferenceDataService;
import org.openlmis.requisition.service.referencedata.PeriodReferenceDataService;
import org.openlmis.requisition.service.referencedata.SupervisoryNodeReferenceDataService;
import org.openlmis.requisition.service.stockmanagement.StockEventStockManagementService;
import org.openlmis.requisition.utils.DateHelper;
import org.openlmis.requisition.validate.DraftRequisitionValidator;
import org.openlmis.requisition.validate.RequisitionValidator;
import org.openlmis.requisition.validate.RequisitionVersionValidator;
import org.openlmis.requisition.settings.service.ConfigurationSettingService;
import org.openlmis.requisition.utils.AuthenticationHelper;
import org.openlmis.requisition.utils.DatePhysicalStockCountCompletedEnabledPredicate;
import org.openlmis.requisition.utils.FacilitySupportsProgramHelper;
import org.openlmis.requisition.utils.StockEventBuilder;
import org.springframework.validation.Errors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings({"PMD.TooManyMethods", "PMD.UnusedPrivateField"})
public class RequisitionControllerTest {
  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @Mock
  private RequisitionRepository requisitionRepository;

  @Mock
  private RequisitionService requisitionService;

  @Mock
  private PeriodService periodService;

  @Mock
  private Requisition initiatedRequsition;

  @Mock
  private Requisition submittedRequsition;

  @Mock
  private Requisition authorizedRequsition;

  @Mock
  private Requisition approvedRequsition;

  @Mock
  private RequisitionTemplate template;

  @Mock
  private BasicRequisitionTemplateDto templateDto;

  @Mock
  private RequisitionValidator validator;

  @Mock
  private DraftRequisitionValidator draftValidator;

  @Mock
  private RequisitionTemplateRepository templateRepository;

  @Mock
  private PermissionService permissionService;

  @Mock
  private BasicRequisitionDtoBuilder basicRequisitionDtoBuilder;

  @Mock
  private RequisitionDtoBuilder requisitionDtoBuilder;

  @Mock
  private FacilitySupportsProgramHelper facilitySupportsProgramHelper;

  @Mock
  private AuthenticationHelper authenticationHelper;

  @Mock
  private OrderableReferenceDataService orderableReferenceDataService;

  @Mock
  private SupervisoryNodeReferenceDataService supervisoryNodeReferenceDataService;

  @Mock
  private ConfigurationSettingService configurationSettingService;

  @Mock
  private RequisitionVersionValidator requisitionVersionValidator;

  @Mock
  private RequisitionStatusProcessor requisitionStatusProcessor;

  @Mock
  private RequisitionStatusNotifier requisitionStatusNotifier;

  @Mock
  private DatePhysicalStockCountCompletedEnabledPredicate predicate;

  @Mock
  private StockEventBuilder inventoryDraftBuilder;

  @Mock
  private StockEventStockManagementService inventoryService;

  @Mock
  private PeriodReferenceDataService periodReferenceDataService;

  @Mock
  private DateHelper dateHelper;

  @InjectMocks
  private RequisitionController requisitionController;

  private UUID programUuid = UUID.randomUUID();
  private UUID facilityUuid = UUID.randomUUID();
  private UUID uuid1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private UUID uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private UUID uuid3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
  private UUID uuid4 = UUID.fromString("00000000-0000-0000-0000-000000000004");
  private UUID uuid5 = UUID.fromString("00000000-0000-0000-0000-000000000005");

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    List<ProcessingPeriodDto> processingPeriods = generateProcessingPeriods();
    when(initiatedRequsition.getStatus()).thenReturn(RequisitionStatus.INITIATED);
    when(submittedRequsition.getStatus()).thenReturn(RequisitionStatus.SUBMITTED);
    when(authorizedRequsition.getStatus()).thenReturn(RequisitionStatus.AUTHORIZED);
    when(approvedRequsition.getStatus()).thenReturn(RequisitionStatus.APPROVED);

    when(submittedRequsition.getId()).thenReturn(uuid3);
    when(authorizedRequsition.getId()).thenReturn(uuid4);
    when(authorizedRequsition.isApprovable()).thenReturn(true);

    when(periodService.getPeriods(programUuid, facilityUuid, false))
        .thenReturn(processingPeriods);
    when(periodService.getPeriods(programUuid, facilityUuid, true))
        .thenReturn(Collections.singletonList(processingPeriods.get(0)));

    when(predicate.exec(any(UUID.class))).thenReturn(true);

    mockRequisitionRepository();

    when(periodReferenceDataService.findOne(any(UUID.class)))
        .thenReturn(mock(ProcessingPeriodDto.class));
  }

  @Test
  public void shouldReturnCurrentPeriodForEmergency() throws Exception {
    when(permissionService.canInitOrAuthorizeRequisition(programUuid, facilityUuid))
        .thenReturn(ValidationResult.success());

    Collection<ProcessingPeriodDto> periods =
        requisitionController.getProcessingPeriodIds(programUuid, facilityUuid, true);

    verify(periodService).getPeriods(programUuid, facilityUuid, true);
    verifyZeroInteractions(periodService, requisitionRepository);

    assertNotNull(periods);
    assertEquals(1, periods.size());

    List<UUID> periodUuids = periods
        .stream()
        .map(ProcessingPeriodDto::getId)
        .collect(Collectors.toList());

    assertTrue(periodUuids.contains(uuid1));
  }

  @Test
  public void shouldSubmitValidInitiatedRequisition() {
    UserDto submitter = mock(UserDto.class);
    when(submitter.getId()).thenReturn(UUID.randomUUID());

    when(permissionService.canSubmitRequisition(uuid1))
        .thenReturn(ValidationResult.success());

    when(initiatedRequsition.getTemplate()).thenReturn(template);
    when(requisitionRepository.findOne(uuid1)).thenReturn(initiatedRequsition);
    when(authenticationHelper.getCurrentUser()).thenReturn(submitter);

    requisitionController.submitRequisition(uuid1);

    verify(initiatedRequsition).submit(eq(Collections.emptyList()), any(UUID.class));
    // we do not update in this endpoint
    verify(initiatedRequsition, never())
        .updateFrom(any(Requisition.class), anyList(), anyBoolean());
  }

  @Test
  public void shouldNotSubmitInvalidRequisition() {
    when(permissionService.canSubmitRequisition(uuid1))
        .thenReturn(ValidationResult.success());
    doAnswer(invocation -> {
      Errors errors = (Errors) invocation.getArguments()[1];
      errors.reject("requisitionLineItems",
          "approvedQuantity is only available during the approval step of the requisition process");
      return null;
    }).when(validator).validate(eq(initiatedRequsition), any(Errors.class));
    when(initiatedRequsition.getId()).thenReturn(uuid1);

    assertThatThrownBy(() -> requisitionController.submitRequisition(uuid1))
        .isInstanceOf(BindingResultException.class);

    verifyNoSubmitOrUpdate(initiatedRequsition);
  }

  @Test(expected = ValidationMessageException.class)
  public void shouldReturnBadRequestWhenRequisitionIdDiffersFromTheOneInUrl() throws Exception {
    RequisitionDto requisitionDto = mock(RequisitionDto.class);
    when(requisitionDto.getId()).thenReturn(uuid1);

    requisitionController.updateRequisition(requisitionDto, uuid2);
  }

  @Test
  public void shouldUpdateRequisition() throws Exception {
    RequisitionDto requisitionDto = mock(RequisitionDto.class);

    when(requisitionDto.getId()).thenReturn(uuid1);
    when(requisitionDto.getFacility()).thenReturn(mock(FacilityDto.class));
    when(requisitionDto.getProgram()).thenReturn(mock(ProgramDto.class));
    when(requisitionDto.getProcessingPeriod()).thenReturn(mock(ProcessingPeriodDto.class));
    when(requisitionDto.getSupervisoryNode()).thenReturn(UUID.randomUUID());

    when(initiatedRequsition.getTemplate()).thenReturn(template);
    when(initiatedRequsition.getSupervisoryNodeId()).thenReturn(null);
    when(initiatedRequsition.getId()).thenReturn(uuid1);

    when(requisitionService.validateCanSaveRequisition(uuid1))
        .thenReturn(ValidationResult.success());
    when(requisitionVersionValidator.validateRequisitionTimestamps(
        any(Requisition.class), any(Requisition.class))).thenReturn(ValidationResult.success());

    requisitionController.updateRequisition(requisitionDto, uuid1);

    assertEquals(template, initiatedRequsition.getTemplate());
    verify(initiatedRequsition).updateFrom(any(Requisition.class), anyList(), eq(true));
    verify(requisitionRepository).save(initiatedRequsition);
    verify(requisitionVersionValidator).validateRequisitionTimestamps(any(Requisition.class),
        eq(initiatedRequsition));
    verifySupervisoryNodeWasNotUpdated(initiatedRequsition);
  }

  @Test
  public void shouldNotUpdateWithInvalidRequisition() {
    RequisitionDto requisitionDto = mock(RequisitionDto.class);
    when(requisitionDto.getTemplate()).thenReturn(templateDto);
    when(requisitionDto.getFacility()).thenReturn(mock(FacilityDto.class));
    when(requisitionDto.getProgram()).thenReturn(mock(ProgramDto.class));
    when(requisitionDto.getProcessingPeriod()).thenReturn(mock(ProcessingPeriodDto.class));
    when(requisitionService.validateCanSaveRequisition(any(UUID.class)))
        .thenReturn(ValidationResult.success());
    when(requisitionVersionValidator.validateRequisitionTimestamps(
        any(Requisition.class), any(Requisition.class))).thenReturn(ValidationResult.success());

    doAnswer(invocation -> {
      Errors errors = (Errors) invocation.getArguments()[1];
      errors.reject("requisitionLineItems[0].beginningBalance", "Bad argument");

      return null;
    }).when(draftValidator).validate(any(Requisition.class), any(Errors.class));

    assertThatThrownBy(() -> requisitionController.updateRequisition(requisitionDto, uuid1))
        .isInstanceOf(BindingResultException.class);

    verify(requisitionService).validateCanSaveRequisition(any(UUID.class));
    verifyNoSubmitOrUpdate(initiatedRequsition);
  }

  @Test
  public void shouldThrowExceptionWhenFacilityOrProgramIdNotFound() throws Exception {
    exception.expect(ValidationMessageException.class);
    requisitionController.initiate(programUuid, null, null, false);
    exception.expect(ValidationMessageException.class);
    requisitionController.initiate(null, facilityUuid, null, false);
  }

  @Test
  public void shouldApproveAuthorizedRequisitionWithParentNode() {
    SupervisoryNodeDto supervisoryNode = mockSupervisoryNode();

    UUID parentNodeId = UUID.randomUUID();
    SupervisoryNodeDto parentNode = mock(SupervisoryNodeDto.class);
    when(parentNode.getId()).thenReturn(parentNodeId);
    when(supervisoryNode.getParentNode()).thenReturn(parentNode);

    setUpApprover();

    requisitionController.approveRequisition(authorizedRequsition.getId());

    verify(requisitionService, times(1)).validateCanApproveRequisition(
        any(Requisition.class),
        any(UUID.class),
        any(UUID.class));

    verify(authorizedRequsition, times(1)).approve(eq(parentNodeId), any(), any());

    verifyZeroInteractions(inventoryDraftBuilder, inventoryService);
  }

  @Test
  public void shouldApproveAuthorizedRequisitionWithoutParentNode() {
    mockSupervisoryNode();
    setUpApprover();

    requisitionController.approveRequisition(authorizedRequsition.getId());

    verify(requisitionService, times(1)).validateCanApproveRequisition(
        any(Requisition.class),
        any(UUID.class),
        any(UUID.class));

    verify(authorizedRequsition, times(1)).approve(eq(null), any(), any());
  }

  /*
  @Test
  public void shouldCreatePhysicalInventoryDraftWhenApprovingRequisitionWithoutParentNode() {
    mockSupervisoryNode();
    setUpApprover();
    StockEventDto inventoryDraft = mock(StockEventDto.class);
    when(inventoryDraftBuilder.fromRequisition(authorizedRequsition))
            .thenReturn(inventoryDraft);

    requisitionController.approveRequisition(authorizedRequsition.getId());

    verify(authorizedRequsition).approve(eq(null), any(), any());
    verify(inventoryDraftBuilder).fromRequisition(authorizedRequsition);
    verify(inventoryService).save(inventoryDraft);
  }
  */

  @Test(expected = PermissionMessageException.class)
  public void shouldNotApproveIfHasNoPermission() {
    mockSupervisoryNode();

    UserDto approver = mock(UserDto.class);
    when(approver.getId()).thenReturn(UUID.randomUUID());
    when(authenticationHelper.getCurrentUser()).thenReturn(approver);

    PermissionMessageException exception = mock(PermissionMessageException.class);
    doThrow(exception).when(requisitionService).validateCanApproveRequisition(
        any(Requisition.class),
        any(UUID.class),
        any(UUID.class));

    requisitionController.approveRequisition(authorizedRequsition.getId());
  }

  @Test
  public void shouldRejectRequisitionWhenUserCanApproveRequisition() {
    when(permissionService.canApproveRequisition(authorizedRequsition.getId()))
        .thenReturn(ValidationResult.success());

    requisitionController.rejectRequisition(authorizedRequsition.getId());

    verify(requisitionService, times(1)).reject(authorizedRequsition.getId());
  }

  @Test
  public void shouldNotRejectRequisitionWhenUserCanNotApproveRequisition() {
    doReturn(ValidationResult.noPermission("notAuthorized"))
        .when(permissionService).canApproveRequisition(uuid4);

    assertThatThrownBy(() -> requisitionController.rejectRequisition(uuid4))
        .isInstanceOf(PermissionMessageException.class);

    verify(requisitionService, times(0)).reject(uuid4);
  }

  @Test
  public void shouldCallRequisitionStatusNotifierWhenReject() {
    when(permissionService.canApproveRequisition(authorizedRequsition.getId()))
        .thenReturn(ValidationResult.success());
    when(requisitionService.reject(authorizedRequsition.getId())).thenReturn(initiatedRequsition);

    requisitionController.rejectRequisition(authorizedRequsition.getId());

    verify(requisitionStatusNotifier).notifyStatusChanged(initiatedRequsition);
  }

  @Test
  public void shouldProcessStatusChangeWhenApprovingRequisition() throws Exception {
    when(requisitionService.validateCanApproveRequisition(any(Requisition.class),
        any(UUID.class),
        any(UUID.class)))
        .thenReturn(ValidationResult.success());
    UserDto approver = mock(UserDto.class);
    when(approver.getId()).thenReturn(UUID.randomUUID());
    when(authenticationHelper.getCurrentUser()).thenReturn(approver);

    requisitionController.approveRequisition(authorizedRequsition.getId());

    verify(requisitionService, times(1)).validateCanApproveRequisition(
        any(Requisition.class),
        any(UUID.class),
        any(UUID.class));

    verify(requisitionStatusProcessor).statusChange(authorizedRequsition);
  }

  @Test
  public void shouldProcessStatusChangeWhenAuthorizingRequisition() throws Exception {
    when(permissionService.canAuthorizeRequisition(submittedRequsition.getId()))
        .thenReturn(ValidationResult.success());
    UserDto submitter = mock(UserDto.class);
    when(submitter.getId()).thenReturn(UUID.randomUUID());
    when(authenticationHelper.getCurrentUser()).thenReturn(submitter);

    mockSupervisoryNodeForAuthorize();

    requisitionController.authorizeRequisition(submittedRequsition.getId());

    verify(requisitionStatusProcessor).statusChange(submittedRequsition);
  }

  private void mockSupervisoryNodeForAuthorize() {
    SupervisoryNodeDto supervisoryNode = mock(SupervisoryNodeDto.class);
    when(supervisoryNode.getId()).thenReturn(UUID.randomUUID());

    when(supervisoryNodeReferenceDataService.findSupervisoryNode(any(), any()))
        .thenReturn(supervisoryNode);
  }

  private SupervisoryNodeDto mockSupervisoryNode() {
    UUID supervisoryNodeId = UUID.randomUUID();
    SupervisoryNodeDto supervisoryNodeDto = mock(SupervisoryNodeDto.class);
    when(supervisoryNodeDto.getId()).thenReturn(supervisoryNodeId);
    when(supervisoryNodeReferenceDataService.findOne(supervisoryNodeId))
        .thenReturn(supervisoryNodeDto);
    when(authorizedRequsition.getSupervisoryNodeId()).thenReturn(supervisoryNodeId);
    return supervisoryNodeDto;
  }

  private List<ProcessingPeriodDto> generateProcessingPeriods() {
    ProcessingPeriodDto period = new ProcessingPeriodDto();
    period.setId(uuid1);
    ProcessingPeriodDto period2 = new ProcessingPeriodDto();
    period2.setId(uuid2);
    ProcessingPeriodDto period3 = new ProcessingPeriodDto();
    period3.setId(uuid3);
    ProcessingPeriodDto period4 = new ProcessingPeriodDto();
    period4.setId(uuid4);
    ProcessingPeriodDto period5 = new ProcessingPeriodDto();
    period5.setId(uuid5);

    List<ProcessingPeriodDto> periods = new ArrayList<>();
    periods.add(period);
    periods.add(period2);
    periods.add(period3);
    periods.add(period4);
    periods.add(period5);

    return periods;
  }

  private void mockRequisitionRepository() {
    when(requisitionRepository.searchRequisitions(uuid1, facilityUuid, programUuid, false))
        .thenReturn(new ArrayList<>());
    when(requisitionRepository.searchRequisitions(uuid2, facilityUuid, programUuid, false))
        .thenReturn(Collections.singletonList(initiatedRequsition));
    when(requisitionRepository.searchRequisitions(uuid3, facilityUuid, programUuid, false))
        .thenReturn(Collections.singletonList(submittedRequsition));
    when(requisitionRepository.searchRequisitions(uuid4, facilityUuid, programUuid, false))
        .thenReturn(Collections.singletonList(authorizedRequsition));
    when(requisitionRepository.searchRequisitions(uuid5, facilityUuid, programUuid, false))
        .thenReturn(Collections.singletonList(approvedRequsition));
    when(requisitionRepository.save(initiatedRequsition))
        .thenReturn(initiatedRequsition);
    when(requisitionRepository.findOne(uuid1))
        .thenReturn(initiatedRequsition);
    when(requisitionRepository.findOne(authorizedRequsition.getId()))
        .thenReturn(authorizedRequsition);
    when(requisitionRepository.findOne(submittedRequsition.getId()))
        .thenReturn(submittedRequsition);
  }

  private void verifyNoSubmitOrUpdate(Requisition requisition) {
    verifyNoMoreInteractions(requisitionService);
    verify(requisition, never()).updateFrom(any(Requisition.class), anyList(), anyBoolean());
    verify(requisition, never()).submit(eq(Collections.emptyList()), any(UUID.class));
  }

  private void verifySupervisoryNodeWasNotUpdated(Requisition requisition) {
    verify(requisition, never()).setSupervisoryNodeId(any());
    assertNull(requisition.getSupervisoryNodeId());
  }

  private void setUpApprover() {
    UserDto approver = mock(UserDto.class);
    when(approver.getId()).thenReturn(UUID.randomUUID());
    when(authenticationHelper.getCurrentUser()).thenReturn(approver);
    when(requisitionService.validateCanApproveRequisition(any(Requisition.class),
            any(UUID.class),
            any(UUID.class)))
            .thenReturn(ValidationResult.success());
  }
}
