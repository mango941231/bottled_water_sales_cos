package cc.mrbird.febs.cos.controller;

import cc.mrbird.febs.common.annotation.Log;
import cc.mrbird.febs.common.utils.FileUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/file")
public class FileController {

    /**
     * 文件上传存放目录，由配置项 febs.uploadPath 指定。
     * 生产环境请配置为绝对路径，避免部署到 Linux 后相对路径随工作目录漂移
     */
    @Value("${febs.uploadPath}")
    private String uploadPath;

    /**
     * 文件上传
     * @param file 上传的文件
     * @return
     */
    @ResponseBody
    @RequestMapping("/fileUpload")
    public String upload(@RequestParam("avatar") MultipartFile file){
        // 1定义要上传文件 的存放路径
         String localPath=uploadPath;
        // 2获得文件名字
        String fileName=file.getOriginalFilename();
        // 2上传失败提示
        String warning="";
        String newFileName = FileUtil.upload(file, localPath, fileName);
        MultipartFile file1 = file;
        if(newFileName != null){
            //上传成功
            warning=newFileName;

        }else{
            warning="上传失败";
        }
        System.out.println(warning);
        return warning;
    }

}
