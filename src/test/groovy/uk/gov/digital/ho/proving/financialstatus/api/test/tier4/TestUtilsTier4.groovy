package uk.gov.digital.ho.proving.financialstatus.api.test.tier4

import org.springframework.context.support.ResourceBundleMessageSource
import uk.gov.digital.ho.proving.financialstatus.domain.CourseTypeChecker
import uk.gov.digital.ho.proving.financialstatus.domain.MaintenanceThresholdCalculatorT4
import uk.gov.digital.ho.proving.financialstatus.domain.StudentTypeChecker

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class TestUtilsTier4 {

    public static def thresholdUrl = "/pttg/financialstatus/v1/t4/maintenance/threshold"

    static getMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource()
        messageSource.setBasename("messages")
        messageSource
    }

    public static def inLondonMaintenance = 1265
    public static def notInLondonMaintenance = 1015
    public static def maxMaintenanceAllowance = 1265
    public static def inLondonDependant = 845
    public static def notInLondonDependant = 680

    public static def generalCappedCourseLength = 9

    public static def susoCappedCourseLength = 2

    public static def pgddsusoCappedCourseLength = 2
    public static def doctorateExtensionFixedCourseLength = 2


    static def getStudentTypeChecker() { new StudentTypeChecker("des", "general", "pgdd", "suso") }

    static def getCourseTypeChecker() { new CourseTypeChecker("main", "pre-sessional", "below-degree") }

    static def maintenanceThresholdServiceBuilder() {
        new MaintenanceThresholdCalculatorT4(
            Clock.fixed(Instant.parse("1900-01-01T01:00:00Z"), ZoneId.of("UTC")),
            inLondonMaintenance,
            notInLondonMaintenance,
            maxMaintenanceAllowance,
            inLondonDependant,
            notInLondonDependant,
            //generalMinCourseLength,
            generalCappedCourseLength,
            //pgddsusoMinCourseLength,
            pgddsusoCappedCourseLength,
            doctorateExtensionFixedCourseLength,
            //susoMinCourseLength,
            susoCappedCourseLength
        )
    }

}
