function out = genDTMFtone( DTMF_char, Fs, duration, amplitude)
%Function to generate a DTMF tone given the low and high freq and the
%sampling frequency. The duration (in ms) of the signal is determined by
%the "duration" parameter. The max amplitude of the signal is determined by
%the "amplitude" parameter.

    if (DTMF_char == '1' || DTMF_char == 1)
        lo = 697;
        hi = 1209;
    elseif (DTMF_char == '2' || DTMF_char == 2)
        lo = 697;
        hi = 1336;
    elseif (DTMF_char == '3' || DTMF_char == 3)
        lo = 697;
        hi = 1477;
    elseif (DTMF_char == '4' || DTMF_char == 4)
        lo = 770;
        hi = 1209;
	elseif (DTMF_char == '5' || DTMF_char == 5)
        lo = 770;
        hi = 1336;
	elseif (DTMF_char == '6' || DTMF_char == 6)
        lo = 770;
        hi = 1477;
	elseif (DTMF_char == '7' || DTMF_char == 7)
        lo = 852;
        hi = 1209;
	elseif (DTMF_char == '8' || DTMF_char == 8)
        lo = 852;
        hi = 1336;
	elseif (DTMF_char == '9' || DTMF_char == 9)
        lo = 852;
        hi = 1477;    
    elseif (DTMF_char == '0' || DTMF_char == 0 || DTMF_char == 10)
        lo = 941;
        hi = 1336;
	elseif (DTMF_char == '*'|| DTMF_char == 11)
        lo = 941;
        hi = 1209;
	elseif (DTMF_char == '#'|| DTMF_char == 12)
        lo = 941;
        hi = 1477;
	elseif (DTMF_char == 'A' || DTMF_char == 'a'|| DTMF_char == 13)
        lo = 697;
        hi = 1633;
	elseif (DTMF_char == 'B' || DTMF_char == 'b'|| DTMF_char == 14)
        lo = 770;
        hi = 1633;
 	elseif (DTMF_char == 'C' || DTMF_char == 'c'|| DTMF_char == 15)
        lo = 852;
        hi = 1633;
    elseif (DTMF_char == 'D' || DTMF_char == 'd'|| DTMF_char == 16)
        lo = 941;
        hi = 1633;
    else
        if (isstr(DTMF_char))
            msg = strcat('"',DTMF_char,'" is not a valid DTMF character');
        else
            msg = 'That is not a valid DTMF character';
        end
        throw(MException('Invalid Character',msg));
    end

    samples = floor(duration*Fs/1000);
    t = transpose(1:samples);
    low = sin(2*pi*lo*t/Fs);
    high = sin(2*pi*hi*t/Fs);
    out = amplitude*(low+high)/2;

end

