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

package org.openlmis.requisition.domain.requisition;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.BooleanUtils.isNotTrue;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.openlmis.requisition.domain.requisition.RequisitionLineItem.ADDITIONAL_QUANTITY_REQUIRED;
import static org.openlmis.requisition.domain.requisition.RequisitionLineItem.ADJUSTED_CONSUMPTION;
import static org.openlmis.requisition.domain.requisition.RequisitionLineItem.AVERAGE_CONSUMPTION;
import static org.openlmis.requisition.domain.requisition.RequisitionLineItem.CALCULATED_ORDER_QUANTITY;
import static org.openlmis.requisition.domain.requisition.RequisitionLineItem.CALCULATED_ORDER_QUANTITY_ISA;
import static org.openlmis.requisition.domain.requisition.RequisitionLineItem.SKIPPED_COLUMN;
import static org.openlmis.requisition.i18n.MessageKeys.ERROR_MUST_BE_INITIATED_TO_BE_SUBMMITED;
import static org.openlmis.requisition.i18n.MessageKeys.ERROR_MUST_BE_SUBMITTED_TO_BE_AUTHORIZED;
import static org.openlmis.requisition.i18n.MessageKeys.ERROR_PROGRAM_DOES_NOT_ALLOW_SKIP;
import static org.openlmis.requisition.i18n.MessageKeys.ERROR_SKIP_FAILED_EMERGENCY;
import static org.openlmis.requisition.i18n.MessageKeys.ERROR_SKIP_FAILED_WRONG_STATUS;
import static org.openlmis.requisition.utils.RequisitionHelper.validateAreRequiredRegularRequisitionFieldsNotFilled;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.javers.core.metamodel.annotation.DiffIgnore;
import org.javers.core.metamodel.annotation.TypeName;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.openlmis.requisition.CurrencyConfig;
import org.openlmis.requisition.domain.BaseTimestampedEntity;
import org.openlmis.requisition.domain.OpenLmisNumberUtils;
import org.openlmis.requisition.domain.RequisitionTemplate;
import org.openlmis.requisition.dto.ApprovedProductDto;
import org.openlmis.requisition.dto.BasicRequisitionTemplateDto;
import org.openlmis.requisition.dto.FacilityDto;
import org.openlmis.requisition.dto.OrderableDto;
import org.openlmis.requisition.dto.ProcessingPeriodDto;
import org.openlmis.requisition.dto.ProgramDto;
import org.openlmis.requisition.dto.ProofOfDeliveryDto;
import org.openlmis.requisition.dto.ProofOfDeliveryLineItemDto;
import org.openlmis.requisition.dto.ReasonDto;
import org.openlmis.requisition.dto.SupplyLineDto;
import org.openlmis.requisition.dto.stockmanagement.StockCardRangeSummaryDto;
import org.openlmis.requisition.errorhandling.ValidationResult;
import org.openlmis.requisition.exception.ValidationMessageException;
import org.openlmis.requisition.service.PermissionService;
import org.openlmis.requisition.utils.Message;
import org.openlmis.requisition.utils.RequisitionHelper;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.profiler.Profiler;
import org.springframework.util.CollectionUtils;

@SuppressWarnings("PMD.TooManyMethods")
@Entity
@TypeName("Requisition")
@Table(name = "requisitions")
@NoArgsConstructor
@AllArgsConstructor
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Requisition extends BaseTimestampedEntity {
  private static final XLogger LOGGER = XLoggerFactory.getXLogger(Requisition.class);

  static final String FACILITY_ID = "facilityId";
  static final String PROGRAM_ID = "programId";
  static final String PROCESSING_PERIOD_ID = "processingPeriodId";
  public static final String TOTAL_CONSUMED_QUANTITY = "totalConsumedQuantity";
  public static final String STOCK_ON_HAND = "stockOnHand";
  static final String SUPERVISORY_NODE_ID = "supervisoryNodeId";
  static final String NUMBER_OF_MONTHS_IN_PERIOD = "numberOfMonthsInPeriod";
  public static final String EMERGENCY_FIELD = "emergency";
  static final String DATE_PHYSICAL_STOCK_COUNT_COMPLETED = "datePhysicalStockCountCompleted";
  public static final String REQUISITION_LINE_ITEMS = "requisitionLineItems";

  private static final int LINE_ITEMS_BATCH_SIZE = 100;

  @OneToMany(
      mappedBy = "requisition",
      cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.REMOVE},
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  @BatchSize(size = LINE_ITEMS_BATCH_SIZE)
  @DiffIgnore
  @Getter
  @Setter
  private List<RequisitionLineItem> requisitionLineItems;

  @Version
  @Getter
  @Setter
  private Long version;

  @Getter
  private String draftStatusMessage;

  @ManyToOne
  @JoinColumn(name = "templateId", nullable = false)
  @DiffIgnore
  @Getter
  @Setter
  private RequisitionTemplate template;

  @Column(nullable = false)
  @Getter
  @Setter
  @Type(type = UUID_TYPE)
  private UUID facilityId;

  @Column(nullable = false)
  @Getter
  @Setter
  @Type(type = UUID_TYPE)
  private UUID programId;

  @Column(nullable = false)
  @Getter
  @Setter
  @Type(type = UUID_TYPE)
  private UUID processingPeriodId;

  @Getter
  @Setter
  @Type(type = UUID_TYPE)
  private UUID supplyingFacilityId;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  @Getter
  @Setter
  private RequisitionStatus status;

  @OneToMany(
      mappedBy = "requisition",
      cascade = CascadeType.ALL)
  @BatchSize(size = STANDARD_BATCH_SIZE)
  @DiffIgnore
  @Getter
  @Setter
  private List<StatusChange> statusChanges = new ArrayList<>();

  @Column(nullable = false)
  @Getter
  @Setter
  private Boolean emergency;

  @Getter
  @Setter
  private Boolean reportOnly;

  @Column(nullable = false)
  @Getter
  @Setter
  private Integer numberOfMonthsInPeriod;

  @Getter
  @Setter
  @Type(type = UUID_TYPE)
  private UUID supervisoryNodeId;

  @ManyToMany
  @JoinTable(name = "requisitions_previous_requisitions",
      joinColumns = {@JoinColumn(name = "requisitionId")},
      inverseJoinColumns = {@JoinColumn(name = "previousRequisitionId")})
  @DiffIgnore
  @Getter
  @Setter
  private List<Requisition> previousRequisitions;

  @ElementCollection(fetch = FetchType.EAGER, targetClass = UUID.class)
  @Column(name = "value")
  @CollectionTable(
      name = "available_products",
      joinColumns = @JoinColumn(name = "requisitionId"))
  @Getter
  @Setter
  @Type(type = UUID_TYPE)
  private Set<UUID> availableProducts;

  @Getter
  @Setter
  @Embedded
  @AttributeOverrides({
      @AttributeOverride(name = "localDate",
          column = @Column(name = "datephysicalstockcountcompleted"))
      })
  private DatePhysicalStockCountCompleted datePhysicalStockCountCompleted;

  @OneToMany(
      cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.REMOVE},
      fetch = FetchType.LAZY,
      orphanRemoval = true)
  @BatchSize(size = STANDARD_BATCH_SIZE)
  @JoinColumn(name = "requisitionId")
  @DiffIgnore
  @Getter
  @Setter
  private List<StockAdjustmentReason> stockAdjustmentReasons = new ArrayList<>();

  @OneToMany(
      mappedBy = "requisition",
      cascade = CascadeType.ALL)
  @BatchSize(size = STANDARD_BATCH_SIZE)
  @DiffIgnore
  @Getter
  private List<RequisitionPermissionString> permissionStrings = new ArrayList<>();

  /**
   * Constructor.
   *
   * @param facilityId         id of the Facility
   * @param programId          id of the Program
   * @param processingPeriodId id of the ProcessingPeriod
   * @param status             status of the Requisition
   * @param emergency          whether this Requisition is emergency
   */
  public Requisition(UUID facilityId, UUID programId, UUID processingPeriodId,
                     RequisitionStatus status, Boolean emergency) {
    this.facilityId = facilityId;
    this.programId = programId;
    this.processingPeriodId = processingPeriodId;
    this.status = status;
    this.emergency = emergency;
    permissionStrings.add(RequisitionPermissionString.newRequisitionPermissionString(this,
        PermissionService.REQUISITION_VIEW, facilityId, programId));
  }

  /**
   * Validates if requisition can be updated.
   */
  public ValidationResult validateCanBeUpdated(
      RequisitionValidationService validationService) {
    return validationService.validateRequisitionCanBeUpdated();
  }

  /**
   * Validates if requisition can be change status.
   */
  public ValidationResult validateCanChangeStatus(LocalDate currentDate,
                                                boolean isDatePhysicalStockCountCompletedEnabled) {
    return new StatusChangeValidationService(this, currentDate,
        isDatePhysicalStockCountCompletedEnabled)
        .validateRequisitionCanChangeStatus();
  }

  /**
   * Returns a set of all orderable IDs in this requisition.
   */
  public Set<UUID> getAllOrderableIds() {
    Set<UUID> orderableIds = Optional
        .ofNullable(requisitionLineItems)
        .orElse(Collections.emptyList())
        .stream()
        .map(RequisitionLineItem::getOrderableId)
        .collect(Collectors.toSet());

    Optional.ofNullable(availableProducts).ifPresent(orderableIds::addAll);

    return orderableIds;
  }

  /**
   * Copy values of attributes into new or updated Requisition.
   *
   * @param requisition            Requisition with new values.
   * @param products               Collection of orderables.
   */
  public void updateFrom(Requisition requisition, Map<UUID, OrderableDto> products,
      boolean isDatePhysicalStockCountCompletedEnabled) {
    LOGGER.entry(requisition, products, isDatePhysicalStockCountCompletedEnabled);
    Profiler profiler = new Profiler("REQUISITION_UPDATE_FROM");
    profiler.setLogger(LOGGER);

    profiler.start("SET_DRAFT_STATUS_MESSAGE");
    this.draftStatusMessage = requisition.draftStatusMessage;

    profiler.start("UPDATE_LINE_ITEMS");
    updateReqLines(requisition.getRequisitionLineItems());

    profiler.start("CALCULATE_AND_VALIDATE_TEMPLATE_FIELDS");
    calculateAndValidateTemplateFields(this.template);

    profiler.start("UPDATE_TOTAL_COST_AND_PACKS_TO_SHIP");
    updateTotalCostAndPacksToShip(products);

    if (isDatePhysicalStockCountCompletedEnabled) {
      profiler.start("SET_DATE_PHYSICAL_STOCK_COUNT_COMPLETED");
      setDatePhysicalStockCountCompleted(requisition.getDatePhysicalStockCountCompleted());
    }

    // do this manually here, since JPA won't catch updates to collections (line items)
    profiler.start("SET_MODIFIED_DATE");
    setModifiedDate(ZonedDateTime.now());

    profiler.stop().log();
    LOGGER.exit();
  }

  /**
   * Initiates the state of a requisition by creating line items based on products.
   *
   * @param template             the requisition template for this requisition to use (based on
   *                             program)
   * @param fullSupplyProducts   the full supply products for this requisitions facility to build
   *                             requisition lines for
   * @param previousRequisitions the previous requisitions for this program/facility. Used for field
   *                             calculations and set previous adjusted consumptions. Pass empty
   *                             list if there are no previous requisitions.
   */
  public void initiate(
      RequisitionTemplate template,
      Collection<ApprovedProductDto> fullSupplyProducts,
      List<Requisition> previousRequisitions,
      int numberOfPreviousPeriodsToAverage,
      ProofOfDeliveryDto proofOfDelivery,
      Map<UUID, Integer> idealStockAmounts,
      UUID initiator,
      StockData stockData,
      List<StockCardRangeSummaryDto> stockCardRangeSummaries) {

    Profiler profiler = new Profiler("REQUISITION_INITIATE_ENTITY");
    profiler.setLogger(LOGGER);
    this.template = template;
    this.previousRequisitions = previousRequisitions;

    profiler.start("SET_LINE_ITEMS");
    if (template.isPopulateStockOnHandFromStockCards()) {
      initiateLineItems(fullSupplyProducts, idealStockAmounts, stockData, stockCardRangeSummaries);
    } else {
      initiateLineItems(fullSupplyProducts, idealStockAmounts, proofOfDelivery, profiler);
    }

    profiler.start("SET_SKIPPED_FROM_PREV_REQUISITION");
    if (isNotTrue(emergency)
        && template.isColumnInTemplateAndDisplayed(SKIPPED_COLUMN)
        && template.isColumnFromPreviousRequisition(SKIPPED_COLUMN)) {
      copySkippedValuesFromPreviousRequisition();
    }

    profiler.start("SET_PREV_ADJ_CONSUMPTION");
    setPreviousAdjustedConsumptions(numberOfPreviousPeriodsToAverage);

    status = RequisitionStatus.INITIATED;

    profiler.start("SET_STATUS_CHANGES");
    statusChanges.add(StatusChange.newStatusChange(this, initiator));

    profiler.stop().log();
  }

  private void copySkippedValuesFromPreviousRequisition() {
    if (!previousRequisitions.isEmpty() && null != previousRequisitions.get(0)) {
      Map<UUID, RequisitionLineItem> productIdToPreviousLine = previousRequisitions.get(0)
          .getRequisitionLineItems().stream().collect(
              toMap(RequisitionLineItem::getOrderableId, identity(),
                  (item1, item2) -> item1));

      getFullSupplyRequisitionLineItems().forEach(currentLine -> {
        RequisitionLineItem previousLine = productIdToPreviousLine.getOrDefault(
            currentLine.getOrderableId(), null);
        currentLine.setSkipped(
            LineItemFieldsCalculator.canSkipLineItem(currentLine, previousLine));
      });
    }
  }

  private void initiateLineItems(Collection<ApprovedProductDto> fullSupplyProducts,
      Map<UUID, Integer> idealStockAmounts, StockData stockData,
      List<StockCardRangeSummaryDto> stockCardRangeSummaries) {
    this.requisitionLineItems = new ArrayList<>();

    if (isNotTrue(emergency)) {
      for (ApprovedProductDto product : fullSupplyProducts) {
        UUID orderableId = product.getOrderable().getId();

        if (isNotTrue(stockData.hasDataFor(orderableId))) {
          continue;
        }

        RequisitionLineItem lineItem = new RequisitionLineItem(this, product);
        lineItem.setIdealStockAmount(extractIdealStockAmount(idealStockAmounts, product));
        lineItem.setStockOnHand(stockData.getStockOnHand(orderableId));
        lineItem.setBeginningBalance(stockData.getBeginningBalance(orderableId));

        lineItem.calculateAndSetStockBasedTotalReceivedQuantity(template, stockCardRangeSummaries);
        lineItem.calculateAndSetStockBasedTotalStockoutDays(
            stockCardRangeSummaries, numberOfMonthsInPeriod);
        lineItem.calculateAndSetStockBasedTotalConsumedQuantity(template, stockCardRangeSummaries);
        lineItem.calculateAndSetStockBasedTotalLossesAndAdjustments(
            template, stockCardRangeSummaries);

        this.requisitionLineItems.add(lineItem);
      }
    }
  }

  private void initiateLineItems(Collection<ApprovedProductDto> fullSupplyProducts,
      Map<UUID, Integer> idealStockAmounts, ProofOfDeliveryDto proofOfDelivery,
      Profiler profiler) {
    this.requisitionLineItems = new ArrayList<>();

    if (isNotTrue(emergency)) {
      for (ApprovedProductDto product : fullSupplyProducts) {
        RequisitionLineItem lineItem = new RequisitionLineItem(this, product);
        lineItem.setIdealStockAmount(extractIdealStockAmount(idealStockAmounts, product));

        this.requisitionLineItems.add(lineItem);
      }
    }

    profiler.start("GET_PREV_BEGINNING_BALANCE");
    List<RequisitionLineItem> nonSkippedFullSupplyItems = null;
    // Firstly, if we display the column ...
    // ... and if the previous requisition exists ...
    if (!previousRequisitions.isEmpty()
        && null != previousRequisitions.get(0)
        && template.isColumnDisplayed(RequisitionLineItem.BEGINNING_BALANCE)) {
      // .. for each line from the current requisition ...
      nonSkippedFullSupplyItems = getNonSkippedFullSupplyRequisitionLineItems();

      Map<UUID, RequisitionLineItem> productIdToPreviousLine = previousRequisitions.get(0)
          .getRequisitionLineItems().stream().collect(
              toMap(RequisitionLineItem::getOrderableId, identity(),
                  (item1, item2) -> item1));

      getNonSkippedFullSupplyRequisitionLineItems().forEach(currentLine -> {
        // ... we try to find line in the previous requisition for the same product ...
        RequisitionLineItem previousLine = productIdToPreviousLine.getOrDefault(
            currentLine.getOrderableId(), null);

        // ... and in the end we use it to calculate beginning balance in a new line.
        currentLine.setBeginningBalance(
            LineItemFieldsCalculator.calculateBeginningBalance(previousLine));
      });
    }

    // Secondly, if Proof Of Delivery exists and it is submitted ...
    profiler.start("SET_RECV_QTY");
    if (null != proofOfDelivery && proofOfDelivery.isSubmitted()) {
      // .. for each line from the current requisition ...
      if (nonSkippedFullSupplyItems == null) {
        nonSkippedFullSupplyItems = getNonSkippedFullSupplyRequisitionLineItems();
      }

      Map<UUID, ProofOfDeliveryLineItemDto> productIdToPodLine = proofOfDelivery
          .getLineItems()
          .stream()
          .filter(li -> null != li.getOrderable())
          .collect(toMap(li -> li.getOrderable().getId(), identity(), (one, two) -> one));

      nonSkippedFullSupplyItems.forEach(requisitionLine -> {
        // ... we try to find line in POD for the same product ...
        ProofOfDeliveryLineItemDto proofOfDeliveryLine = productIdToPodLine.getOrDefault(
            requisitionLine.getOrderableId(), null);

        // ... and if line exists we set value for Total Received Quantity (B) column
        if (null != proofOfDeliveryLine) {
          requisitionLine.setTotalReceivedQuantity(
              OpenLmisNumberUtils.zeroIfNull(proofOfDeliveryLine.getQuantityAccepted())
          );
        }
      });
    }
  }

  private Integer extractIdealStockAmount(Map<UUID, Integer> idealStockAmounts,
                                          ApprovedProductDto product) {
    String commodityType = product.getOrderable().getCommodityTypeIdentifier();
    return isNotBlank(commodityType)
        ? idealStockAmounts.get(UUID.fromString(commodityType))
        : null;
  }

  /**
   * Submits this requisition.
   *
   * @param products orderable products that will be used by line items to update packs to ship.
   */
  public void submit(Map<UUID, OrderableDto> products, UUID submitter, boolean skipAuthorize) {
    if (!status.isSubmittable()) {
      throw new ValidationMessageException(
          new Message(ERROR_MUST_BE_INITIATED_TO_BE_SUBMMITED, getId()));
    }

    if (isNotTrue(emergency)) {
      validateAreRequiredRegularRequisitionFieldsNotFilled(template,
          getNonSkippedFullSupplyRequisitionLineItems(), getId());
    }

    updateConsumptions();
    updateTotalCostAndPacksToShip(products);

    status = RequisitionStatus.SUBMITTED;
    statusChanges.add(StatusChange.newStatusChange(this, submitter));

    if (skipAuthorize) {
      LOGGER.debug("Skipping authorize step.");
      prepareRequisitionForApproval(submitter);
    }
    setModifiedDate(ZonedDateTime.now());
  }

  /**
   * Authorize this Requisition.
   *
   * @param products orderable products that will be used by line items to update packs to ship.
   */
  public void authorize(Map<UUID, OrderableDto> products, UUID authorizer) {
    if (!RequisitionStatus.SUBMITTED.equals(status)) {
      throw new ValidationMessageException(
          new Message(ERROR_MUST_BE_SUBMITTED_TO_BE_AUTHORIZED, getId()));
    }

    updateConsumptions();
    updateTotalCostAndPacksToShip(products);
    prepareRequisitionForApproval(authorizer);
    setModifiedDate(ZonedDateTime.now());
  }

  /**
   * Check if the requisition is approvable.
   *
   */
  public boolean isApprovable() {
    return status.duringApproval();
  }

  /**
   * Checks whether the requisition status allows for its deletion.
   *
   * @return true if the requisition status is within those that allow deletion; false otherwise
   */
  public boolean isDeletable() {
    return isPreAuthorize() || status.isSkipped();
  }

  /**
   * Approves given requisition.
   *
   * @param nodeId      supervisoryNode that has a supply line for the requisition's program.
   * @param products    orderable products that will be used by line items to update packs to ship.
   * @param supplyLines supplyLineDtos of the supervisoryNode that has
   *                    a supply line for the requisition's program.
   * @param approver    user who approves this requisition.
   */
  public void approve(UUID nodeId, Map<UUID, OrderableDto> products,
      Collection<SupplyLineDto> supplyLines, UUID approver) {
    if (isTrue(reportOnly)) {
      status = RequisitionStatus.RELEASED_WITHOUT_ORDER;
    } else {
      if (CollectionUtils.isEmpty(supplyLines) && nodeId != null) {
        status = RequisitionStatus.IN_APPROVAL;
        supervisoryNodeId = nodeId;
      } else {
        status = RequisitionStatus.APPROVED;
      }
    }

    updateConsumptions();
    updateTotalCostAndPacksToShip(products);
    setModifiedDate(ZonedDateTime.now());

    statusChanges.add(StatusChange.newStatusChange(this, approver));
  }

  /**
   * Rejects given requisition.
   */
  public void reject(Map<UUID, OrderableDto> products, UUID rejector) {
    status = RequisitionStatus.REJECTED;
    updateConsumptions();
    updateTotalCostAndPacksToShip(products);
    setModifiedDate(ZonedDateTime.now());

    statusChanges.add(StatusChange.newStatusChange(this, rejector));
  }

  /**
   * Release the requisition.
   */
  public void release(UUID releaser) {
    status = RequisitionStatus.RELEASED;
    setModifiedDate(ZonedDateTime.now());

    statusChanges.add(StatusChange.newStatusChange(this, releaser));
  }

  /**
   * Release the requisition.
   */
  public void releaseWithoutOrder(UUID releaser) {
    status = RequisitionStatus.RELEASED_WITHOUT_ORDER;
    setModifiedDate(ZonedDateTime.now());

    statusChanges.add(StatusChange.newStatusChange(this, releaser));
  }

  /**
   * Skip the requisition.
   */
  public void skip(boolean periodsSkippable, UUID userId) {
    if (!periodsSkippable) {
      throw new ValidationMessageException(new Message(ERROR_PROGRAM_DOES_NOT_ALLOW_SKIP));
    }

    if (!status.isSubmittable()) {
      throw new ValidationMessageException(new Message(ERROR_SKIP_FAILED_WRONG_STATUS));
    }

    if (isTrue(emergency)) {
      throw new ValidationMessageException(new Message(ERROR_SKIP_FAILED_EMERGENCY));
    }

    for (RequisitionLineItem item : requisitionLineItems) {
      item.skipLineItem(template);
    }

    status = RequisitionStatus.SKIPPED;
    statusChanges.add(StatusChange.newStatusChange(this, userId));
    setModifiedDate(ZonedDateTime.now());
  }

  /**
   * Finds first RequisitionLineItem that have productId property equals to the given productId
   * argument.
   *
   * @param productId UUID of orderable product
   * @return first RequisitionLineItem that have productId property equals to the given productId
   *         argument; otherwise null;
   */
  public RequisitionLineItem findLineByProductId(UUID productId) {
    if (null == requisitionLineItems) {
      return null;
    }

    return requisitionLineItems
        .stream()
        .filter(e -> Objects.equals(productId, e.getOrderableId()))
        .findFirst()
        .orElse(null);
  }

  public void setDraftStatusMessage(String draftStatusMessage) {
    this.draftStatusMessage = (draftStatusMessage == null) ? "" : draftStatusMessage;
  }

  public boolean isPreAuthorize() {
    return status.isPreAuthorize();
  }

  /**
   * Filter out requisitionLineItems that are skipped.
   *
   * @return requisitionLineItems that are not skipped
   */
  public List<RequisitionLineItem> getNonSkippedRequisitionLineItems() {
    return filterLineItems(false, null);
  }

  /**
   * Filter out requisitionLineItems that are skipped and not-full supply.
   *
   * @return non-skipped full supply requisition line items
   */
  public List<RequisitionLineItem> getNonSkippedFullSupplyRequisitionLineItems() {
    return filterLineItems(false, false);
  }

  /**
   * Filter out requisitionLineItems that are skipped and full supply.
   *
   * @return non-skipped non-full supply requisition line items
   */
  public List<RequisitionLineItem> getNonSkippedNonFullSupplyRequisitionLineItems() {
    return filterLineItems(false, true);
  }

  /**
   * Filter out requisitionLineItems that are not skipped.
   *
   * @return requisitionLineItems that are skipped
   */
  List<RequisitionLineItem> getSkippedRequisitionLineItems() {
    return filterLineItems(true, null);
  }

  List<RequisitionLineItem> getFullSupplyRequisitionLineItems() {
    return filterLineItems(null, false);
  }

  private List<RequisitionLineItem> filterLineItems(Boolean skipped, Boolean nonFullSupply) {
    return Optional
        .ofNullable(requisitionLineItems)
        .orElse(Collections.emptyList())
        .stream()
        .filter(line ->
            null == skipped || Objects.equals(skipped, line.isLineSkipped()))
        .filter(line ->
            null == nonFullSupply || Objects.equals(nonFullSupply, line.isNonFullSupply()))
        .collect(toList());
  }

  /**
   * Calculates combined cost of all requisition line items.
   *
   * @return sum of total costs.
   */
  public Money getTotalCost() {
    return calculateTotalCostForLines(requisitionLineItems);
  }

  /**
   * Calculates combined cost of non-full supply non-skipped requisition line items.
   *
   * @return sum of total costs.
   */
  public Money getNonFullSupplyTotalCost() {
    return calculateTotalCostForLines(getNonSkippedNonFullSupplyRequisitionLineItems());
  }

  /**
   * Calculates combined cost of full supply non-skipped requisition line items.
   *
   * @return sum of total costs.
   */
  public Money getFullSupplyTotalCost() {
    return calculateTotalCostForLines(getNonSkippedFullSupplyRequisitionLineItems());
  }

  /**
   * Export this object to the specified exporter (DTO).
   *
   * @param exporter exporter to export to
   */
  public void export(Requisition.Exporter exporter) {
    exporter.setId(id);
    exporter.setCreatedDate(getCreatedDate());
    exporter.setModifiedDate(getModifiedDate());
    exporter.setStatus(status);
    exportStatusChanges(exporter);
    exporter.setEmergency(emergency);
    exporter.setReportOnly(reportOnly);
    exporter.setSupplyingFacility(supplyingFacilityId);
    exporter.setSupervisoryNode(supervisoryNodeId);
    exporter.setDraftStatusMessage(draftStatusMessage);
    if (datePhysicalStockCountCompleted != null) {
      exporter.setDatePhysicalStockCountCompleted(
          datePhysicalStockCountCompleted.getLocalDate());
    }
  }

  private void exportStatusChanges(Exporter exporter) {
    Optional<Supplier<StatusChange.Exporter>> factory = exporter.provideStatusChangeExporter();

    if (factory.isPresent()) {
      Supplier<StatusChange.Exporter> generator = factory.get();

      // we use the array here to create a new collection object
      // that will be resistant to changes that could be
      // provided by other threads and could cause
      // the java.util.ConcurrentModificationException exception
      StatusChange[] statusChangesArray = Optional
          .ofNullable(statusChanges)
          .orElse(Collections.emptyList())
          .toArray(new StatusChange[0]);

      for (StatusChange statusChange : statusChangesArray) {
        StatusChange.Exporter statusChangeExporter = generator.get();
        statusChange.export(statusChangeExporter);

        exporter.addStatusChange(statusChangeExporter);
      }
    }
  }

  /**
   * Find latest status change by date created.
   *
   * @return recent status change
   */
  public StatusChange getLatestStatusChange() {
    return statusChanges.stream()
        .max(Comparator.comparing(BaseTimestampedEntity::getCreatedDate,
            Comparator.nullsFirst(Comparator.naturalOrder())))
        .orElse(null);
  }

  /**
   * Sets appropriate value for Previous Adjusted Consumptions field in
   * each {@link RequisitionLineItem}.
   */
  void setPreviousAdjustedConsumptions(int numberOfPreviousPeriodsToAverage) {
    List<RequisitionLineItem> previousRequisitionLineItems = RequisitionHelper
        .getNonSkippedLineItems(previousRequisitions.subList(0, numberOfPreviousPeriodsToAverage));

    RequisitionHelper.forEachLine(requisitionLineItems,
        line -> {
          List<RequisitionLineItem> withProductId = RequisitionHelper
              .findByProductId(previousRequisitionLineItems, line.getOrderableId());
          List<Integer> adjustedConsumptions = RequisitionHelper
              .mapToAdjustedConsumptions(withProductId);

          line.setPreviousAdjustedConsumptions(adjustedConsumptions);
        });
  }

  private void prepareRequisitionForApproval(UUID user) {
    populateApprovedQuantity();
    RequisitionHelper.forEachLine(getSkippedRequisitionLineItems(),
        RequisitionLineItem::resetData);
    status = RequisitionStatus.AUTHORIZED;
    statusChanges.add(StatusChange.newStatusChange(this, user));
  }

  private Money calculateTotalCostForLines(List<RequisitionLineItem> requisitionLineItems) {
    Money defaultValue = Money.of(CurrencyUnit.of(CurrencyConfig.CURRENCY_CODE), 0);

    if (requisitionLineItems.isEmpty()) {
      return defaultValue;
    }

    Optional<Money> money = requisitionLineItems.stream()
        .map(RequisitionLineItem::getTotalCost).filter(Objects::nonNull).reduce(Money::plus);

    return money.orElse(defaultValue);
  }

  private void calculateAndValidateTemplateFields(RequisitionTemplate template) {
    getNonSkippedFullSupplyRequisitionLineItems()
        .forEach(line ->
            line.calculateAndSetFields(template, stockAdjustmentReasons, numberOfMonthsInPeriod));
  }

  private void updateConsumptions() {


    if (template.isColumnInTemplateAndDisplayed(ADJUSTED_CONSUMPTION)) {
      getNonSkippedFullSupplyRequisitionLineItems().forEach(line -> line.setAdjustedConsumption(
          LineItemFieldsCalculator.calculateAdjustedConsumption(line, numberOfMonthsInPeriod,
              this.template.isColumnInTemplateAndDisplayed(ADDITIONAL_QUANTITY_REQUIRED))
      ));
    }

    if (template.isColumnInTemplateAndDisplayed(AVERAGE_CONSUMPTION)) {
      getNonSkippedFullSupplyRequisitionLineItems().forEach(
          RequisitionLineItem::calculateAndSetAverageConsumption);
    }
  }

  private void updateTotalCostAndPacksToShip(Map<UUID, OrderableDto> products) {
    getNonSkippedRequisitionLineItems().forEach(line -> {
      line.updatePacksToShip(products.get(line.getOrderableId()));
      line.setTotalCost(LineItemFieldsCalculator
          .calculateTotalCost(line, CurrencyUnit.of(CurrencyConfig.CURRENCY_CODE)));
    });
  }

  private void populateApprovedQuantity() {
    for (RequisitionLineItem line : getNonSkippedRequisitionLineItems()) {
      if (template.isColumnDisplayed(CALCULATED_ORDER_QUANTITY)) {
        line.setApprovedQuantity(Optional
            .ofNullable(line.getRequestedQuantity())
            .orElse(line.getCalculatedOrderQuantity()));
      } else if (template.isColumnInTemplate(CALCULATED_ORDER_QUANTITY_ISA)
          && template.isColumnDisplayed(CALCULATED_ORDER_QUANTITY_ISA)) {
        line.setApprovedQuantity(Optional
            .ofNullable(line.getRequestedQuantity())
            .orElse(line.getCalculatedOrderQuantityIsa()));
      } else {
        line.setApprovedQuantity(line.getRequestedQuantity());
      }
    }
  }

  private void updateReqLines(Collection<RequisitionLineItem> newLineItems) {
    if (null == newLineItems) {
      return;
    }

    if (null == requisitionLineItems) {
      requisitionLineItems = new ArrayList<>();
    }

    List<RequisitionLineItem> updatedList = new ArrayList<>();

    for (RequisitionLineItem item : newLineItems) {
      RequisitionLineItem existing = requisitionLineItems
          .stream()
          .filter(l -> l.getId().equals(item.getId()))
          .findFirst()
          .orElse(null);

      if (null == existing) {
        item.setRequisition(this);
        updatedList.add(item);
      } else {
        existing.setRequisition(this);
        existing.updateFrom(item);
        updatedList.add(existing);
      }
    }

    requisitionLineItems.clear();
    requisitionLineItems.addAll(updatedList);
  }

  public interface Exporter {
    void setId(UUID id);

    void setCreatedDate(ZonedDateTime createdDate);

    void setModifiedDate(ZonedDateTime createdDate);

    void setStatus(RequisitionStatus status);

    void setEmergency(Boolean emergency);
    
    void setReportOnly(Boolean reportOnly);

    void setSupplyingFacility(UUID supplyingFacility);

    void setSupervisoryNode(UUID supervisoryNode);

    void setTemplate(BasicRequisitionTemplateDto template);

    void setDraftStatusMessage(String draftStatusMessage);

    void setDatePhysicalStockCountCompleted(LocalDate localDate);

    void setStockAdjustmentReasons(List<ReasonDto> reasonDto);

    Optional<Supplier<StatusChange.Exporter>> provideStatusChangeExporter();

    void addStatusChange(StatusChange.Exporter providedExporter);
  }

  public interface Importer {
    UUID getId();

    ZonedDateTime getCreatedDate();

    ZonedDateTime getModifiedDate();

    List<RequisitionLineItem.Importer> getRequisitionLineItems();

    FacilityDto getFacility();

    ProgramDto getProgram();

    ProcessingPeriodDto getProcessingPeriod();

    RequisitionStatus getStatus();

    Boolean getEmergency();

    UUID getSupplyingFacility();

    UUID getSupervisoryNode();

    String getDraftStatusMessage();

    Set<OrderableDto> getAvailableNonFullSupplyProducts();

    LocalDate getDatePhysicalStockCountCompleted();
  }
}
