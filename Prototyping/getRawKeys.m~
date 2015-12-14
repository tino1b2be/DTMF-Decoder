% Copyright (c) 2015 Tinotenda Chemvura
% 
% Permission is hereby granted, free of charge, to any person obtaining a copy
% of this software and associated documentation files (the "Software"), to deal
% in the Software without restriction, including without limitation the rights
% to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
% copies of the Software, and to permit persons to whom the Software is
% furnished to do so, subject to the following conditions:
% 
% 
% The above copyright notice and this permission notice shall be included in
% all copies or substantial portions of the Software.
% 
% 
% THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
% IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
% FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
% AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
% LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
% OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
% THE SOFTWARE.
%
% http://opensource.org/licenses/MIT
%

% -*- texinfo -*- 
% @deftypefn {Function File} {@var{retval} =} makeFrames (@var{input1}, @var{input2})
%
% @seealso{}
% @end deftypefn

%Author: Tinotenda Chemvura @tino1b2be
%Created: 2015-12-04

function rawKeys = getRawKeys( dft_data  )
% Function to decode each of the frames to get the number represented by
% the data from the Goertzel Calculation. the argument "magData" is a
% vector with the magnitudes of each of the DTMF Frequencies in the frame
% they were extracted from.

% the output "rawKeys" will be the number represented by each frame. This
% has the numbers decoded from every frame present. frames that represent a
% "silence" will be shows as "_" strings

    rawKeys = repmat('~',[1,size(dft_data,2)]);
    %freq_low = [697,770,582,941];
    %freq_high = [1209,1336,1477,1633];
    
    %go through each vector and first determine whether it is a silence or
    %an actual DTMF. if it is a silent, add '_' to the output. if it is not
    %a silence, get the indicies of the two highest peaks.

    % after some observation, noticed that the mean of the frames with DTMF
    % frequencies is much higher that the mean of "silent" frames,
    
    % find the 3 largest averages in the frames.
    % not efficient to go through the whole data set there
    % * get top 3 in the first 20 frames
    % * find average of those 3 peaks
    % * the silent frames will be frames with a mean that is less than 10%
    % of the average of the top 3 peaks.
    
    % get the top 3 frames from first 20
    if (length(dft_data) >= 50)
        first20 = sort(mean(dft_data(:,1:50)),'descend');
    else
        first20 = sort(mean(dft_data),'descend');
    end
    
    if (size(first20,2) < 6)
        topAvg = mean(first20(1));
    else
        topAvg = mean(first20(2:6)); % average of the top 5
    end
    
    % go through all the frames, decode frames with a mean greatere than
    % 10% of 'topAvg'
    
    for j = 1 : size(dft_data,2) % for each decoded frame
        %get index of highest DTMF high and low frequencies
        if (mean(dft_data(:,j)) < (0.61 * topAvg))
            rawKeys(j) = '_';
            
        else
            [a,low] = max(dft_data(1:4,j));
            [a,high] = max(dft_data(5:8,j));

            % find the corresponding frequencies
            if (low == 1) %low = 697
                if (high == 1)      %high = 1209
                    rawKeys(j) = '1';
                elseif (high == 2) %high = 1336
                    rawKeys(j) = '2';
                elseif (high == 3) %high = 1477
                    rawKeys(j) = '3';
                elseif (high == 4) %high = 1633
                    rawKeys(j) = 'A';
                end
            elseif (low == 2) %low = 770
                if (high == 1)      %high = 1209
                    rawKeys(j) = '4';
                elseif (high == 2) %high = 1336
                    rawKeys(j) = '5';
                elseif (high == 3) %high = 1477
                    rawKeys(j) = '6';
                elseif (high == 4) %high = 1633
                    rawKeys(j) = 'B';
                end
            elseif (low == 3) %low = 852
                if (high == 1)      %high = 1209
                    rawKeys(j) = '7';
                elseif (high == 2) %high = 1336
                    rawKeys(j) = '8';
                elseif (high == 3) %high = 1477
                    rawKeys(j) = '9';
                elseif (high == 4) %high = 1633
                    rawKeys(j) = 'C';
                end
            elseif (low == 4) %low = 941
                if (high == 1)      %high = 1209
                    rawKeys(j) = '*';
                elseif (high == 2) %high = 1336
                    rawKeys(j) = '0';
                elseif (high == 3) %high = 1477
                    rawKeys(j) = '#';
                elseif (high == 4) %high = 1633
                    rawKeys(j) = 'D';
                end
            end
        end
        
    end % end of loop through each frame
    
end % end of function

