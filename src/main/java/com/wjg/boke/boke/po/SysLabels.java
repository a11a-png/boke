package com.wjg.boke.boke.po;

import java.io.Serializable;
import lombok.Data;

/**
 * sys_labels
 * @author 
 */
@Data
public class SysLabels implements Serializable {
    private Integer labelId;

    private String labelName;

    private String labelAlias;

    private String labelDescription;

    private static final long serialVersionUID = 1L;
}