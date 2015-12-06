% Copyright (C) 2015 Tinotenda Chemvura
% 
% This program is free software; you can redistribute it and/or modify it
% under the terms of the GNU General Public License as published by
% the Free Software Foundation; either version 3 of the License, or
% (at your option) any later version.
% 
% This program is distributed in the hope that it will be useful,
% but WITHOUT ANY WARRANTY; without even the implied warranty of
% MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
% GNU General Public License for more details.
% 
% You should have received a copy of the GNU General Public License
% along with this program.  If not, see <http://www.gnu.org/licenses/>.

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

    rawKeys = repmat('~',[1,length(dft_data)]);
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
    if (length(dft_data) >= 20)
        first20 = sort(mean(dft_data(:,1:20)),'descend');
    else
        first20 = sort(mean(dft_data),'descend');
    end
    
    topAvg = mean(first20(1:3)); % average of the top 3
    
    % go through all the frames, decode frames with a mean greatere than
    % 10% of 'topAvg'
    
    for j = 1 : length(dft_data) % for each decoded frame
        %get index of highest DTMF high and low frequencies
        if (mean(dft_data(:,j)) < (0.2 * topAvg))
            rawKeys(j) = '_';
            
        else
            [mag,low] = max(dft_data(1:4,j));
            [mag,high] = max(dft_data(5:8,j));

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

