package org.openlmis.requisition.validate;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.requisition.domain.AvailableRequisitionColumn;
import org.openlmis.requisition.domain.AvailableRequisitionColumnOption;
import org.openlmis.requisition.domain.RequisitionTemplate;
import org.openlmis.requisition.domain.RequisitionTemplateColumn;
import org.openlmis.requisition.domain.SourceType;
import org.openlmis.requisition.exception.RequisitionTemplateColumnException;
import org.springframework.validation.Errors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class RequisitionTemplateValidatorTest {

  private static final String COLUMN_NAME = "test";

  @InjectMocks
  private RequisitionTemplateValidator validator;

  private Errors errors = mock(Errors.class);

  @Test
  public void shouldRejectIfRequestedQuantityAndExplanationIsDisplayedValuesAreDifferent()
      throws RequisitionTemplateColumnException {

    RequisitionTemplate requisitionTemplate = generateTemplate();
    requisitionTemplate.changeColumnDisplay(
        RequisitionTemplateValidator.REQUESTED_QUANTITY_EXPLANATION, false);
    requisitionTemplate.changeColumnSource(RequisitionTemplateValidator.REQUESTED_QUANTITY,
        SourceType.USER_INPUT);

    requisitionTemplate.changeColumnSource(
        RequisitionTemplateValidator.REQUESTED_QUANTITY_EXPLANATION, SourceType.USER_INPUT);

    validator.validate(requisitionTemplate, errors);

    verify(errors).rejectValue(eq(RequisitionTemplateValidator.COLUMNS_MAP),
        eq(RequisitionTemplateValidator.REQUESTED_QUANTITY_EXPLANATION
            + " must be displayed when requested quantity is displayed."));
  }

  @Test
  public void shouldRejectIfSourceIsNotAvailableInColumn()
      throws RequisitionTemplateColumnException {
    RequisitionTemplate requisitionTemplate = generateTemplate();

    requisitionTemplate.changeColumnSource(COLUMN_NAME, SourceType.USER_INPUT);
    requisitionTemplate.getColumnsMap()
        .get(COLUMN_NAME).getColumnDefinition().getSources().clear();

    validator.validate(requisitionTemplate, errors);

    verify(errors).rejectValue(eq(RequisitionTemplateValidator.COLUMNS_MAP),
        eq(RequisitionTemplate.SOURCE + SourceType.USER_INPUT
            + RequisitionTemplate.WARNING_SUFFIX));
  }

  @Test
  public void shouldRejectIfOptionIsNotAvailableInColumn()
      throws RequisitionTemplateColumnException {
    RequisitionTemplate requisitionTemplate = generateTemplate();
    AvailableRequisitionColumnOption option = new AvailableRequisitionColumnOption(
        requisitionTemplate.getColumnsMap().get(COLUMN_NAME)
            .getColumnDefinition(), "option1", "label1");
    Set<AvailableRequisitionColumnOption> options = new HashSet<>();
    options.add(option);
    requisitionTemplate.getColumnsMap().get(COLUMN_NAME).getColumnDefinition().setOptions(options);
    requisitionTemplate.changeColumnOption(COLUMN_NAME, option);

    requisitionTemplate.getColumnsMap().get(COLUMN_NAME)
        .getColumnDefinition().getOptions().clear();

    validator.validate(requisitionTemplate, errors);

    verify(errors).rejectValue(eq(RequisitionTemplateValidator.COLUMNS_MAP),
        eq(RequisitionTemplate.OPTION + option.getOptionName()
            + RequisitionTemplate.WARNING_SUFFIX));
  }

  @Test
  public void shouldRejectIfTotalStockoutDaysFieldIsNotDisplayed()
      throws RequisitionTemplateColumnException {
    RequisitionTemplate requisitionTemplate =
        getRequisitionTemplateForTestAdjustedConsumptionField();
    requisitionTemplate.changeColumnDisplay(
        RequisitionTemplateValidator.TOTAL_STOCKOUT_DAYS, false);

    validator.validate(requisitionTemplate, errors);

    verify(errors).rejectValue(eq(RequisitionTemplateValidator.COLUMNS_MAP),
        eq(RequisitionTemplateValidator.TOTAL_STOCKOUT_DAYS
            + RequisitionTemplateValidator.ADJUSTED_CONSUMPTION_MUST_BE_CALCULATED_INFORMATION));
  }

  @Test
  public void shouldRejectIfTotalConsumedQuantityFieldIsNotDisplayed()
      throws RequisitionTemplateColumnException {
    RequisitionTemplate requisitionTemplate =
        getRequisitionTemplateForTestAdjustedConsumptionField();
    requisitionTemplate.changeColumnDisplay(
        RequisitionTemplateValidator.TOTAL_CONSUMED_QUANTITY, false);

    validator.validate(requisitionTemplate, errors);

    verify(errors).rejectValue(eq(RequisitionTemplateValidator.COLUMNS_MAP),
        eq(RequisitionTemplateValidator.TOTAL_CONSUMED_QUANTITY
            + RequisitionTemplateValidator.ADJUSTED_CONSUMPTION_MUST_BE_CALCULATED_INFORMATION));
  }

  private RequisitionTemplate generateTemplate() throws RequisitionTemplateColumnException {
    Map<String, RequisitionTemplateColumn> columnMap = new HashMap<>();
    columnMap.put(RequisitionTemplateValidator.REQUESTED_QUANTITY,
        generateTemplateColumn(RequisitionTemplateValidator.REQUESTED_QUANTITY, "J", true));
    columnMap.put(RequisitionTemplateValidator.REQUESTED_QUANTITY_EXPLANATION,
        generateTemplateColumn(RequisitionTemplateValidator.REQUESTED_QUANTITY_EXPLANATION,
            "W", true));
    RequisitionTemplateColumn column = generateTemplateColumn(COLUMN_NAME, "T", true);

    columnMap.put(COLUMN_NAME, column);

    return new RequisitionTemplate(columnMap);
  }

  private RequisitionTemplate getRequisitionTemplateForTestAdjustedConsumptionField()
      throws RequisitionTemplateColumnException {
    Map<String, RequisitionTemplateColumn> columnMap = new HashMap<>();
    columnMap.put(RequisitionTemplateValidator.TOTAL_CONSUMED_QUANTITY,
        generateTemplateColumn(RequisitionTemplateValidator.TOTAL_CONSUMED_QUANTITY, "C", true));
    columnMap.put(RequisitionTemplateValidator.TOTAL_STOCKOUT_DAYS,
        generateTemplateColumn(RequisitionTemplateValidator.TOTAL_STOCKOUT_DAYS,
            "X", true));
    columnMap.put(RequisitionTemplateValidator.ADJUSTED_CONSUMPTION,
        generateTemplateColumn(RequisitionTemplateValidator.ADJUSTED_CONSUMPTION,
            "N", true));

    RequisitionTemplate requisitionTemplate = new RequisitionTemplate(columnMap);
    requisitionTemplate.changeColumnSource(RequisitionTemplateValidator.ADJUSTED_CONSUMPTION,
        SourceType.CALCULATED);
    return requisitionTemplate;
  }

  private RequisitionTemplateColumn generateTemplateColumn(String name, String indicator,
                                                           boolean displayed)
      throws RequisitionTemplateColumnException {
    AvailableRequisitionColumn columnDefinition = new AvailableRequisitionColumn();
    columnDefinition.setName(name);
    columnDefinition.setIndicator(indicator);
    columnDefinition.setIsDisplayRequired(false);
    Set<SourceType> sources = new HashSet<>();
    sources.add(SourceType.USER_INPUT);
    sources.add(SourceType.CALCULATED);
    columnDefinition.setSources(sources);

    RequisitionTemplateColumn requisitionTemplateColumn =
        new RequisitionTemplateColumn(columnDefinition);
    requisitionTemplateColumn.setName(name);
    requisitionTemplateColumn.setIsDisplayed(displayed);
    return requisitionTemplateColumn;
  }
}
