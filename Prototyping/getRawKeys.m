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

function rawKeys = getRawKeys( magData )
% Function to decode each of the frames to get the number represented by
% the data from the Goertzel Calculation. the argument "magData" is a
% vector with the magnitudes of each of the DTMF Frequencies in the frame
% they were extracted from.

% the output "rawKeys" will be the number represented by each frame. This
% has the numbers decoded from every frame present. frames that represent a
% "silence" will be shows as "@" sings

    freq_low = [697,770,582,941];
    freq_high = [1209,1336,1477,1633];
    
    %go through each vector and first determine whether it is a silence or
    %an actual DTMF. if it is a silent, add '@' to the output. if it is not
    %a silence, get the indicies of the two highest peaks. these two peaks
    %must have a magnitude greater than one

end

