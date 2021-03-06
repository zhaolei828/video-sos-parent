package com.derder.api.file;

import com.derder.admin.BaseApiController;
import com.derder.business.model.EmrgContact;
import com.derder.business.model.User;
import com.derder.business.model.Video;
import com.derder.business.service.UserService;
import com.derder.business.service.VideoService;
import com.derder.business.vo.AddressVO;
import com.derder.common.util.DateUtil;
import com.derder.common.util.ErrorCode;
import com.derder.common.util.JsonUtil;
import com.derder.common.util.ResultData;
import org.apache.commons.collections.map.HashedMap;
import org.apache.log4j.Logger;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.ui.velocity.VelocityEngineUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: zhaolei
 * Date: 16-12-15
 * Time: 下午11:20
 */
@RestController
public class FileUploadController extends BaseApiController {
    private final Logger log = Logger.getLogger(getClass());

    @Value("${os.file.system.path.split}")
    private String PATH_SPLIT;

    @Value("${file.upload.location}")
    private String UPLOAD_LOCATION;

    @Value("${spring.mail.username}")
    private String SEND_EMAIL;

    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private VelocityEngine velocityEngine;

    @Autowired
    private UserService userService;

    @Autowired
    private VideoService videoService;

    @RequestMapping(value="/sendVideo", method= RequestMethod.POST)
    public @ResponseBody
    ResultData handleFileUpload(@RequestParam("file") MultipartFile file,
                                @RequestParam("location") String locationJson){
        if (!file.isEmpty()) {
            long userId = getUserId();
            String fileName = file.getOriginalFilename();
            String suffix = fileName.substring(fileName.indexOf("."),fileName.length());
            String newFileName = userId + "_" + System.currentTimeMillis() + suffix;
            String newFileDir = UPLOAD_LOCATION + datePath();
            File newDir = new File(newFileDir);
            if (!newDir.exists()){
                newDir.mkdirs();
            }
            String newFilePath = newFileDir + newFileName;
            try {
                byte[] bytes = file.getBytes();
                BufferedOutputStream stream =
                        new BufferedOutputStream(new FileOutputStream(new File(newFilePath)));
                stream.write(bytes);
                stream.close();
            } catch (Exception e) {
                log.error("#####文件上传异常",e);
                return getResultData(false,"",ErrorCode.UPLOAD_FILE_EXCEPTION);
            }

            try {
                User user = getUser();
                List<EmrgContact> list = userService.getEmrgContactListByUser(userId);
                String[] to = new String[list.size()+1];
                to[0] = user.getUserEmail();
                for (int i = 1;i <= list.size();i++) {
                    to[i] = list.get(i-1).getEmail();
                }
                sendEmail(to,newFilePath);
            } catch (MessagingException e) {
                log.error("#####发送邮件异常",e);
                return getResultData(false,"",ErrorCode.SEND_EMAIL_EXCEPTION);
            }
            Video video;
            try {
                AddressVO addressVO = JsonUtil.parseObject(locationJson,AddressVO.class);
                if (null != addressVO) {
                    video = new Video();
                    video.setCityCode(addressVO.getCityCode());
                    video.setDistrict(addressVO.getAdCode());
                    video.setUploadAddressJson(locationJson);
                    video.setVideoName(newFileName);
                    video.setVideoPath(newFilePath);
                    video.setUploadUser(userId);
                    video.setUploadTime(new Date());

                    video.setCreateBy(userId);
                    video.setCreateTime(new Date());
                    video.setUpdateBy(userId);
                    video.setUpdateTime(new Date());

                    videoService.addVideo(video);
                }
            } catch (Exception e) {
                log.error("####获取位置信息失败.",e);
            }
            return getResultData(true,"","","");
        } else {
            return getResultData(false,"", ErrorCode.UPLOAD_FILE_CANNOT_EMPTY);
        }
    }

    void sendEmail(String[] to,String videofilePath) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
        helper.setFrom(SEND_EMAIL);
        helper.setTo(to);
        helper.setSubject("主题：模板邮件");
        Map<String, Object> model = new HashedMap();
        model.put("username", "didi");
        String text = VelocityEngineUtils.mergeTemplateIntoString(
                velocityEngine, "template.vm", "UTF-8", model);
        helper.setText(text, true);
        File videofile = new File(videofilePath);
        FileSystemResource fileSystemResource = new FileSystemResource(videofile);
        helper.addAttachment(videofile.getName(), fileSystemResource);
        mailSender.send(mimeMessage);
    }

    String datePath(){
        Date now = new Date();
        String todayStr = DateUtil.formatDate(now,"yyyy-MM-dd");
        String[] ymdArr = todayStr.split("-");
        return ymdArr[0] + PATH_SPLIT + ymdArr[1] + PATH_SPLIT + ymdArr[2] + PATH_SPLIT;
    }
}
