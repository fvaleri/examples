package it.fvaleri.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "Api message model")
public class ApiMessage {
    @ApiModelProperty(value="Code", example="1")
    @JsonProperty
    private int code;

    @ApiModelProperty(value="Message", example="Error text")
    @JsonProperty
    private String message;

    public ApiMessage(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
