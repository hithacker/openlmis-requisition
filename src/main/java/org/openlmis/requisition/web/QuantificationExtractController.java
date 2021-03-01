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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.openlmis.requisition.domain.requisition.RequisitionLineItem;
import org.openlmis.requisition.dto.FacilityDto;
import org.openlmis.requisition.dto.OrderableDto;
import org.openlmis.requisition.exception.ValidationMessageException;
import org.openlmis.requisition.repository.QuantificationExtractRepository;
import org.openlmis.requisition.service.referencedata.FacilityReferenceDataService;
import org.openlmis.requisition.service.referencedata.OrderableReferenceDataService;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.profiler.Profiler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@Transactional
public class QuantificationExtractController extends BaseController {

  private static final XLogger XLOGGER = XLoggerFactory.getXLogger(RequisitionLineItem.class);

  private static final String[] HEADERS = {
      "Facility Name", "Facility Code", "Product Name", "Product Code", "Unit",
      "Adjusted Consumption"
  };

  @Autowired
  private QuantificationExtractRepository quantificationExtractRepository;

  @Autowired
  private FacilityReferenceDataService facilityReferenceDataService;

  @Autowired
  private OrderableReferenceDataService orderableReferenceDataService;

  /**
   * Downloads csv file with all catalog items.
   */
  @GetMapping(value = "/quantificationExtract")
  @ResponseBody
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<Resource> download() {
    Profiler profiler = new Profiler("DOWNLOAD_CATALOG_ITEMS_AS_FILE");
    profiler.setLogger(XLOGGER);

    Iterable<RequisitionLineItem> items = quantificationExtractRepository.findAll();

    ByteArrayInputStream in = extractToCsv(items);

    InputStreamResource file = new InputStreamResource(in);

    String filename = "quantification-extract.csv";

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
        .contentType(MediaType.parseMediaType("application/csv"))
        .body(file);
  }

  /**
   * Extract the quantification data to CSV. Return a ByteArrayInputStream
   */
  public ByteArrayInputStream extractToCsv(Iterable<RequisitionLineItem> items) {
    final CSVFormat format = CSVFormat.DEFAULT.withQuoteMode(QuoteMode.MINIMAL).withHeader(HEADERS);

    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      CSVPrinter csvPrinter = new CSVPrinter(new PrintWriter(out), format);
      for (RequisitionLineItem item : items) {
        FacilityDto facilityDto = facilityReferenceDataService.findOne(
            item.getRequisition().getFacilityId());

        OrderableDto orderableDto = orderableReferenceDataService.findOne(
            item.getOrderable().getId()
        );

        List<String> data = Arrays.asList(
            facilityDto.getName(),
            facilityDto.getCode(),
            orderableDto.getFullProductName(),
            orderableDto.getProductCode(),
            orderableDto.getDispensable().getDispensingUnit(),
            String.valueOf(item.getAdjustedConsumption())
        );

        csvPrinter.printRecord(data);
      }

      csvPrinter.flush();
      return new ByteArrayInputStream(out.toByteArray());
    } catch (IOException e) {
      throw new ValidationMessageException("fail to import data to CSV file: " + e.getMessage());
    }
  }
}
