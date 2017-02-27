package uk.gov.digital.ho.proving.financialstatus.api

import java.lang.{Boolean => JBoolean}
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.digital.ho.proving.financialstatus.audit.AuditActions
import uk.gov.digital.ho.proving.financialstatus.audit.AuditActions.nextId
import uk.gov.digital.ho.proving.financialstatus.audit.AuditEventPublisher
import uk.gov.digital.ho.proving.financialstatus.audit.AuditEventType
import uk.gov.digital.ho.proving.financialstatus.audit.configuration.DeploymentDetails
import uk.gov.digital.ho.proving.financialstatus.authentication.Authentication
import uk.gov.digital.ho.proving.financialstatus.domain.CourseTypeChecker
import uk.gov.digital.ho.proving.financialstatus.domain.StudentTypeChecker
import uk.gov.digital.ho.proving.financialstatus.domain.UserProfile
import uk.gov.digital.ho.proving.financialstatus.domain.conditioncodes.ConditionCodesCalculationResult
import uk.gov.digital.ho.proving.financialstatus.domain.conditioncodes.ConditionCodesCalculatorProvider
import uk.gov.digital.ho.proving.financialstatus.domain.conditioncodes.ConditionCodesParameterError

@RestController
@ControllerAdvice
@RequestMapping(value = Array(ConditionCodesServiceTier4.ConditionCodeTier4Url))
class ConditionCodesServiceTier4  @Autowired()(val auditor: AuditEventPublisher,
                                               val authenticator: Authentication,
                                               val deploymentConfig: DeploymentDetails,
                                               val conditionCodesCalculatorProvider: ConditionCodesCalculatorProvider,
                                               val studentTypeChecker: StudentTypeChecker,
                                               val courseTypeChecker: CourseTypeChecker
                                              ) extends FinancialStatusBaseController {

  private val LOGGER = LoggerFactory.getLogger(classOf[ConditionCodesServiceTier4])

  @RequestMapping(method = Array(RequestMethod.GET))
  def calculateConditionCodes(@RequestParam(value = "studentType") studentType: Optional[String],
                              @RequestParam(value = "dependantsOnly") dependantsOnly: Boolean,
                              @RequestParam(value = "dependants", required = false, defaultValue = "0") dependants: Optional[Integer],
                              @RequestParam(value = "courseStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) courseStartDate: Optional[LocalDate],
                              @RequestParam(value = "courseEndDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) courseEndDate: Optional[LocalDate],
                              @RequestParam(value = "courseType", required = false) courseType: Optional[String],
                              @RequestParam(value = "recognisedBodyOrHEI", required = false) recognisedBodyOrHEI: Optional[JBoolean],
                              @CookieValue(value = "kc-access") kcToken: Optional[String]
                             ): ResponseEntity[ConditionCodesResponse] = {

    val accessToken: Option[String] = kcToken

    val userProfile: Option[UserProfile] = accessToken.flatMap(authenticator.getUserProfileFromToken)

    def withAudit(calculateConditionCodes: => Validated[ConditionCodesParameterError, ConditionCodesCalculationResult]): Validated[ConditionCodesParameterError, ConditionCodesCalculationResult] = {
      val auditRequestParamsData = Map[String, AnyRef](
        "studentType" -> toOptionString(studentType),
        "dependantsOnly" -> dependantsOnly.asInstanceOf[JBoolean],
        "dependants" -> toOptionInt(dependants),
        "courseStartDate" -> toOptionLocalDate(courseStartDate),
        "courseEndDate" -> toOptionLocalDate(courseEndDate),
        "courseType" -> toOptionString(courseType),
        "auditRequestParamsData" -> toOptionBoolean(recognisedBodyOrHEI)
      )
      val correlationId = nextId
      auditor.publishEvent(generateRequestAuditEvent(userProfile, correlationId, auditRequestParamsData))
      val result = calculateConditionCodes
      val auditResultParamsData = result match {
        case Valid(validatedResult) =>
          Map[String, AnyRef](
            "applicantCode" -> validatedResult.applicant,
            "partnerCode" -> validatedResult.partner,
            "childCode" -> validatedResult.child
          )
        case Invalid(invalidResult) =>
          Map[String, AnyRef]("invalidResult" -> invalidResult)
      }

      auditor.publishEvent(generateResultAuditEvent(userProfile, correlationId, auditRequestParamsData))
      result
    }

    val validatedStudentType = studentTypeChecker.getStudentType(studentType.getOrElse("Unknown").toLowerCase)
    val validatedCourseType = courseTypeChecker.getCourseType(courseType.getOrElse("Unknown").toLowerCase)

    val result: Validated[ConditionCodesParameterError, ConditionCodesCalculationResult] = withAudit {
      val calculator = conditionCodesCalculatorProvider.provide(validatedStudentType)
      calculator.calculateConditionCodes(dependantsOnly, dependants, courseStartDate, courseEndDate, validatedCourseType, recognisedBodyOrHEI)
    }
    result match {
      case Valid(x) =>
        val conditionCodesResponse = conditionCodesResultResponseConverter(x)
        new ResponseEntity(conditionCodesResponse, HttpStatus.OK)
      case Invalid(y) => ??? // FIXME: Implement error case response
    }
  }



  private def generateRequestAuditEvent(userProfile: Option[UserProfile], correlationId: UUID, auditRequestParamsData: Map[String, AnyRef]) = {
    AuditActions.auditEvent(
      deploymentConfig = deploymentConfig,
      principal = userProfile.map(_.id).getOrElse("anonymous"),
      auditEventType = AuditEventType.CONDITION_CODES_REQUEST,
      id = correlationId,
      data = auditRequestParamsData
    )
  }

  private def generateResultAuditEvent(userProfile: Option[UserProfile], correlationId: UUID, auditResultParamsData: Map[String, AnyRef]) = {
    AuditActions.auditEvent(
      deploymentConfig = deploymentConfig,
      principal = userProfile.map(_.id).getOrElse("anonymous"),
      auditEventType = AuditEventType.CONDITION_CODES_RESULT,
      id = correlationId,
      data = auditResultParamsData
    )
  }

  def conditionCodesResultResponseConverter(result: ConditionCodesCalculationResult): ConditionCodesResponse =
    ConditionCodesResponse(result.applicant.map(_.value), result.partner.map(_.value),result.child.map(_.value))

}
object ConditionCodesServiceTier4 {
  final val ConditionCodeTier4Url = "/pttg/financialstatus/v1/t4/conditioncodes"
}
