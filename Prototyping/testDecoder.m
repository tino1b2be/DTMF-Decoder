function out = testDecoder( filename )
% Function to test the DTMF decoder
    out = '';
    temp = dir(filename);
    seq = temp.name(1:end-4); % strip off the extention on the filename

    data = decodeDTMF(filename);
    if (length(data) == length(seq))
        if (data ~= seq)
            out = strcat('**  FAILED : "',filename, '" decoded to "',data,'" instead of "',seq,'"');
        else
            out = strcat('$$  Passed : "',seq,'"');
        end
    else
        out = strcat('*%  FAILED : "',filename, '" decoded to "',data,'" instead of "',seq,'"');
    end
    
end

