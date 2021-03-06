package com.tusspringboot.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.tusspringboot.upload.api.FileInfo;
import com.tusspringboot.upload.api.UploadService;
import com.tusspringboot.upload.impl.PartInfo;

/**
 * Created by cvirtucio on 4/18/2017.
 */

@CrossOrigin
@Controller
@RequestMapping("/upload")
public class UploadController {

    @Autowired
    UploadService uploadService;

    @GetMapping("/file/{id}")
    public ResponseEntity readUpload(
            @RequestHeader(name="fileName") String fileName,
            @RequestHeader(name="partNumbers") List<Long> partNumbers
    ) {
    	
    	
        List<FileInfo> partInfoList = partNumbers.stream()
                .map(num -> PartInfo.builder().fileName(fileName).partNumber(num).build())
                .collect(Collectors.toList());

        try {
            return onExists(uploadService.getCurrentOffsets(fileName, partInfoList));
        } catch (IOException e) {
            return onNotExist(fileName);
        }
    }

    @PostMapping("/files")
    public ResponseEntity createUpload(
            @RequestHeader(name="fileName") String fileName
    ) {
        try {
            return onCreateDir(uploadService.getDirectoryPath(fileName));
        } catch (IOException e) {
            return onFailedCreateDir(fileName, e.getMessage());
        }
    }

    @PatchMapping("/file/{id}")
    public ResponseEntity updateUpload(
            @RequestHeader(name="fileName") String fileName,
            @RequestHeader(name="partNumber") Long partNumber,
            @RequestHeader(name="uploadOffset") Long uploadOffset,
            @RequestHeader(name="uploadLength") Long uploadLength,
            @RequestHeader(name="fileSize") Long fileSize,
            @RequestHeader(name="userName") String userName,
            InputStream inputStream
    ) {
        PartInfo partInfo = PartInfo
                .builder()
                .fileSize(fileSize)
                .fileName(fileName)
                .partNumber(partNumber)
                .offset(uploadOffset)
                .length(uploadLength)
                .userName(userName)
                .inputStream(inputStream)
                .build();

        try {
            return onComplete(uploadService.write(partInfo));
        } catch (IOException e) {
            return onInterrupt(partInfo, e.getMessage());
        }
    }

    @PostMapping("/files/complete")
    public ResponseEntity completeUpload( 
            @RequestHeader(name="fileName") String fileName,
            @RequestHeader(name="fileExt") String fileExt,
            @RequestHeader(name="partNumbers") List<Long> partNumbers,
            @RequestHeader(name="fileSize") Long fileSize
    ) {
        List<FileInfo> partInfoList = partNumbers.stream()
                .map(partNumber -> { 
                    return PartInfo.builder()
                            .fileName(fileName)
                            .fileExt(fileExt)
                            .partNumber(partNumber)
                            .fileSize(fileSize).build();
                })
                .collect(Collectors.toList());

        try {
            return onConcatenate(uploadService.concat(partInfoList));
        } catch (IOException e) {
            return onFailedConcatenate(fileName, e.getMessage());
        }
    }

    @RequestMapping(value="/destroy", method=RequestMethod.DELETE)
    public String destroyUpload() {
        return "Destroying upload.";
    }

    private ResponseEntity onConcatenate(Long totalBytesUploaded) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("totalBytesUploaded", totalBytesUploaded.toString())
                .build();
    }

    private ResponseEntity onFailedConcatenate(String fileName, String errorMessage) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("fileName", fileName)
                .body(errorMessage);
    }

    private ResponseEntity onCreateDir(String fileDir) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("fileDir", fileDir)
                .build();
    }

    private ResponseEntity onExists(List<Long> currentOffsetList) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(currentOffsetList);
    }

    private ResponseEntity onNotExist(String fileName) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("fileName", fileName)
                .body("newUpload");
    }

    private ResponseEntity onComplete(PartInfo partInfo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("newOffset", partInfo.getOffset().toString())
                .build();
    }

    private ResponseEntity onInterrupt(PartInfo partInfo, String errorMessage) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("partNumber", partInfo.getPartNumber().toString())
                .body(errorMessage);
    }

    private ResponseEntity onFailedCreateDir(String fileName, String errorMessage) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("fileName", fileName)
                .body(errorMessage);
    }
}
